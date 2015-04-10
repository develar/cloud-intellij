package org.intellij.flux

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.ResourceService
import org.jetbrains.json.MapMemberWriter

class IdeaResourceService : ResourceService {
  override fun get(projectName: String, path: String?, requestorHash: String?, includeContents: Boolean, result: Result) {
    result.write {
      when {
        path == null -> getRootDirectoryContent(projectName)
        path.startsWith("classpath:") -> getClasspathResource(path, result)
        else -> getResource(projectName, path, requestorHash, includeContents)
      }
    }
  }

  private fun MapMemberWriter.getRootDirectoryContent(projectName: String) {
    val project = findReferencedProject(projectName)
    if (project == null) {
      "error"("not found")
      return
    }

    var topLevelCount = 0
    var lastTopLevelDir: VirtualFile? = null

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
            "Location"("m:${module.getName()}")
          }

          topLevelCount++
          lastTopLevelDir = contentRoot
        }
      }
    }

    if (topLevelCount == 1) {
      describeDirectory(lastTopLevelDir!!, "topLevelChildren")
    }
  }

  private fun MapMemberWriter.describeDirectory(directory: VirtualFile, fieldName: String = "children") {
    array(fieldName) {
      for (file in directory.getChildren()) {
        try {
          map {
            "name"(file.getName())
            if (!file.isDirectory()) {
              "length"(file.getLength())
              val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file)
              "hash"(if (cachedDocument == null) DigestUtils.sha1Hex(file.getInputStream()) else cachedDocument.getImmutableCharSequence().sha1())
              "lastSaved"(file.getTimeStamp())
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
    val file = findReferencedFile(resourcePath, projectName)
    if (file == null) {
      "error"("not found")
      return
    }

    if (file.isDirectory()) {
      if (includeContents) {
        describeDirectory(file)
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
  }

  private fun MapMemberWriter.getClasspathResource(resourcePath: String, result: Result) {
    val typeName = resourcePath.substring("classpath:/".length())
    val fileByPath = JarFileSystem.getInstance().findFileByPath(typeName)
    if (fileByPath == null) {
      "error"("not found")
      return
    }

    val content = BinaryFileTypeDecompilers.INSTANCE.forFileType(fileByPath.getFileType()).decompile(fileByPath)
    "readonly"(true)
    "content"(content)
    "hash"(content.sha1())
    "type"("file")
  }
}

