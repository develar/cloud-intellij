package org.intellij.flux

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.scratch.ScratchFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
import org.intellij.lang.regexp.RegExpFileType
import org.jetbrains.flux.Base64
import org.jetbrains.flux.ResourceService
import org.jetbrains.flux.Result
import org.jetbrains.json.MapMemberWriter
import java.awt.GraphicsEnvironment
import java.awt.Transparency
import javax.activation.MimetypesFileTypeMap
import javax.imageio.ImageIO

private val FILE_MIMETYPE_MAP = MimetypesFileTypeMap()

class IdeaResourceService : ResourceService {
  override fun contentTypes(result: Result) {
    result.array {
      val byteOut = BufferExposingByteArrayOutputStream()
      val builder = StringBuilder()
      for (fileType in FileTypeManager.getInstance().getRegisteredFileTypes()) {
        if (fileType is UnknownFileType ||
                fileType is ScratchFileType ||
                fileType is FakeFileType ||
                fileType is RegExpFileType ||
                fileType.getName() == EnforcedPlainTextFileTypeFactory.ENFORCED_PLAIN_TEXT ||
                fileType.getDefaultExtension().isEmpty()) {
          continue
        }

        map {
          var extends: String? = null
          "id"(fun (): CharSequence? {
            var mimeType: String? = null
            if (fileType is LanguageFileType) {
              mimeType = when (fileType.getName()) {
                "JSCS", "JSHint", "ESLint", "SourceMap" -> {
                  extends = "application/json"
                  fileType.getName().toLowerCase()
                }
                "JavaScript" -> "application/javascript"
                "TypeScript" -> "application/typescript"
                "Literate CoffeeScript" -> "text/litcoffee"
                else -> {
                  val mimeTypes = fileType.getLanguage().getMimeTypes()
                  if (mimeTypes.isEmpty()) {
                    null
                  }
                  else {
                    val id = mimeTypes[0]
                    when (id) {
                      "text/java" -> "text/x-java-source"
                      "text/dtd" -> "application/xml-dtd"
                      "text/xml" -> "application/xml"
                      else -> id
                    }
                  }
                }
              }
            }

            if (mimeType == null) {
              mimeType = FILE_MIMETYPE_MAP.getContentType("f.${fileType.getDefaultExtension()}")
            }
            if (mimeType == null || (mimeType == "application/octet-stream" && !fileType.isBinary())) {
              mimeType = "text/${fileType.getDefaultExtension().toLowerCase()}"
            }
            return mimeType
          })

          "name"(fun (): CharSequence {
            val name = fileType.getDescription()
            val postfix = " files"
            if (name.endsWith(postfix)) {
              return name.substring(0, name.length() - postfix.length())
            }
            return name
          })

          if (fileType is ProjectFileType || fileType is ModuleFileType) {
            "extends"("application/xml")
          }
          else if (extends != null) {
            "extends"(extends)
          }

          val icon = fileType.getIcon()
          if (icon != null) {
            val image = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(icon.getIconWidth(), icon.getIconHeight(), Transparency.TRANSLUCENT)
            val g = image.createGraphics()
            icon.paintIcon(null, g, 0, 0)
            g.dispose()

            ImageIO.write(image, "png", byteOut)
            builder.append("data:image/png;base64,").append(Base64.encode(byteOut.getInternalBuffer(), byteOut.size()))
            "image"(builder)

            builder.setLength(0)
            byteOut.reset()
          }

          array("extension") {
            +fileType.getDefaultExtension()
          }
        }
      }
    }
  }

  override fun get(projectName: String, path: String?, requestorHash: String?, includeContents: Boolean, result: Result) {
    result.map {
      when {
        path == null -> getRootDirectoryContent(projectName)
        path.startsWith("classpath:") -> getClasspathResource(path)
        else -> getResource(projectName, path, requestorHash, includeContents)
      }
    }
  }

  private fun MapMemberWriter.getRootDirectoryContent(projectName: String) {
    val project = findReferencedProject(projectName)
    if (project == null) {
      "error"(404)
      return
    }

    val fileTypeRegistry = FileTypeRegistry.getInstance()
    array("children") {
      val directoryIndex = DirectoryIndex.getInstance(project)
      for (module in ModuleManager.getInstance(project).getModules()) {
        if (module.isDisposed()) {
          continue
        }

        for (contentRoot in ModuleRootManager.getInstance(module).getContentRoots()) {
          val info = directoryIndex.getInfoForFile(contentRoot)
          if (!info.isInProject()) {
            // is excluded or ignored
            // todo include excluded directories
            continue
          }
          if (module != info.getModule()) {
            // maybe 2 modules have the same content root
            continue
          }

          val parent = contentRoot.getParent()
          if (parent != null) {
            val parentInfo = directoryIndex.getInfoForFile(parent)
            if (parentInfo.isInProject() && parentInfo.getModule() != null) {
              // inner content - skip it
              continue
            }
          }

          map {
            "name"(contentRoot.getName())
            "location"("m:${module.getName()}")

            describeDirectory(contentRoot, directoryIndex, fileTypeRegistry)
          }
        }
      }
    }
  }

  private fun MapMemberWriter.describeDirectory(directory: VirtualFile, directoryIndex: DirectoryIndex, fileTypeRegistry: FileTypeRegistry) {
    array("children") {
      val children = directory.getChildren()
      for (file in children) {
        try {
          val isDirectory = file.isDirectory()
          if (isDirectory) {
            val info = directoryIndex.getInfoForFile(file)
            if (!info.isInProject()) {
              continue
            }
          }
          else if (fileTypeRegistry.isFileIgnored(file)) {
            continue
          }

          map {
            "name"(file.getName())
            if (!isDirectory) {
              "length"(file.getLength())
              val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file)
              "hash"(if (cachedDocument == null) DigestUtils.sha1Hex(file.getInputStream()) else cachedDocument.getImmutableCharSequence().sha1())
              "lastSaved"(file.getTimeStamp())
            }
            else if (children.size() == 1) {
              describeDirectory(file, directoryIndex, fileTypeRegistry)
            }
          }
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
    }
  }

  private fun MapMemberWriter.getResource(projectName: String, resourcePath: String, requestorHash: String?, includeContents: Boolean) {
    val project = findReferencedProject(projectName)
    val file = project?.findFile(resourcePath)
    if (file == null) {
      "error"(404)
      return
    }

    if (file.isDirectory()) {
      if (includeContents) {
        describeDirectory(file, DirectoryIndex.getInstance(project), FileTypeRegistry.getInstance())
      }
      return
    }

    val token = ReadAction.start()
    val content: CharSequence?
    try {
      content = file.getDocument()?.getImmutableCharSequence()
    }
    finally {
      token.finish()
    }

    val hash = if (content == null) DigestUtils.sha1Hex(file.getInputStream()) else content.sha1()
    if (requestorHash == hash) {
      return
    }

    "lastSaved"(file.getTimeStamp())
    "length"(file.getLength())
    "hash"(hash)
    if (includeContents) {
      "content"(content)
    }
    else {
      "name"(file.getName())
    }
  }

  private fun MapMemberWriter.getClasspathResource(resourcePath: String) {
    val typeName = resourcePath.substring("classpath:/".length())
    val fileByPath = JarFileSystem.getInstance().findFileByPath(typeName)
    if (fileByPath == null) {
      "error"(404)
      return
    }

    val content = BinaryFileTypeDecompilers.INSTANCE.forFileType(fileByPath.getFileType()).decompile(fileByPath)
    "readonly"(true)
    "content"(content)
    "hash"(content.sha1())
    "type"("file")
  }
}

