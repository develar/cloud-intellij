package org.intellij.flux

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.jdom.Element
import org.jetbrains.flux.Result
import org.jetbrains.json.MapMemberWriter

class IdeaEditorService() : IdeaContentAssistService {
  /**
   * "Unknown symbol" -> WRONG_REFERENCES_ATTRIBUTES
   * "Error" -> ERRORS_ATTRIBUTES
   */
  override fun editorStyles(result: Result) {
    result.map {
      val xml = Element("dump")
      (EditorColorsManager.getInstance().getGlobalScheme() as AbstractColorsScheme).writeExternal(xml)
      for (option in xml.getChildren("option")) {
        val value = option.getAttributeValue("value")
        // why it could be null?
        if (value != null) {
          val name = option.getAttributeValue("name")
          if (name == "EDITOR_FONT_SIZE") {
            name(value.toInt())
          }
          else {
            name(value)
          }
        }
      }

      map("colors") {
        for (option in xml.getChild("colors").getChildren("option")) {
          val value = option.getAttributeValue("value")
          // why it could be null?
          if (value != null) {
            option.getAttributeValue("name")("#" + value)
          }
        }
      }

      map("styles") {
        for (style in xml.getChild("attributes").getChildren("option")) {
          val styleName = style.getAttributeValue("name")
          if (!(styleName == "WRONG_REFERENCES_ATTRIBUTES" || styleName == "ERRORS_ATTRIBUTES")) {
            continue
          }

          map(styleName) {
            @loop for (attribute in style.getChild("value").getChildren("option")) {
              val value = attribute.getAttributeValue("value")!!
              if (value.isNullOrEmpty()) {
                continue
              }

              var name = attribute.getAttributeValue("name")
              when (name) {
                "FOREGROUND" -> "color"("#$value")
                "BACKGROUND" -> "background-color"("#$value")
                "ERROR_STRIPE_COLOR" -> continue @loop
                "EFFECT_TYPE" -> {
                  // reuse orion style
                }
                else -> {
                  LOG.warn("Unsupported style attribute $name ($styleName)")
                }
              }
            }
          }
        }
      }
    }
  }

  override fun computeProblems(projectName: String, resourcePath: String, result: Result) {
    val project = findReferencedProject(projectName)
    val document: Document?
    val accessToken = ReadAction.start()
    try {
      document = project?.findFile(resourcePath)?.getDocument()
    }
    finally {
      accessToken.finish()
    }

    if (document == null) {
      result.map {
        "error"(404)
      }
    }
    else {
      highlighterService(project!!).scheduleInspect(document, result)
    }
  }

  override fun MapMemberWriter.computeNavigation(projectName: String, resourcePath: String, offset: Int): Boolean {
    val referencedFile: VirtualFile?
    val project: Project?

    val accessToken = ReadAction.start()
    try {
      if (resourcePath.startsWith("classpath:/")) {
        val typeName = resourcePath.substring("classpath:/".length())
        referencedFile = JarFileSystem.getInstance().findFileByPath(typeName)
        project = findReferencedProject(projectName)
      }
      else {
        referencedFile = findReferencedFile(resourcePath, projectName)
        project = referencedFile?.findProject()
      }

      if (referencedFile == null || project == null) {
        return false
      }

      val document = FileDocumentManager.getInstance().getDocument(referencedFile)
      if (document == null) {
        return false
      }

      val resolve = getTargetElement(offset, project, document, false)
      if (resolve == null) {
        return false
      }

      val containingFile = resolve.getContainingFile()

      var virtualFile = containingFile.getVirtualFile() ?: containingFile.getOriginalFile().getVirtualFile()
      // todo jar
      string("path") {
        if (virtualFile!!.isInLocalFileSystem()) {
          VfsUtilCore.getRelativePath(virtualFile, project.getBaseDir())
        }
        else {
          "classpath:/${virtualFile!!.getPath()}"
        }
      }

      var textRange: TextRange? = null
      if (resolve is PsiNameIdentifierOwner) {
        val nameIdentifier = resolve.getNameIdentifier()
        if (nameIdentifier != null) {
          textRange = nameIdentifier.getTextRange()
        }
      }
      if (textRange == null) {
        textRange = resolve.getTextRange()
      }
      "offset"(textRange!!.getStartOffset())
      "length"(textRange.getLength())
      return true
    }
    finally {
      accessToken.finish()
    }
  }
}