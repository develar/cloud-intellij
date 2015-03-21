package org.intellij.flux

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiNameIdentifierOwner
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.NavigationService

class IntellijNavigationService() : NavigationService {
  override fun navigate(request: Map<String, Any>, result: Result) {
    val projectName = request.get("project") as String
    val resourcePath = request.get("resource") as String
    result.writeIf {
      if (computeNavigation("$projectName/$resourcePath", request.get("offset") as Int, it)) {
        it.name("project").value(projectName)
        it.name("resource").value(resourcePath)
        it.name("callback_id").value(request.get("callback_id") as Int)
        it.name("requestSenderID").value(request.get("requestSenderID") as String)
        true
      }
      else {
        false
      }
    }
  }

  private fun computeNavigation(requestorResourcePath: String, offset: Int, writer: JsonWriter): Boolean {
    val projectName = requestorResourcePath.substring(0, requestorResourcePath.indexOf('/'))
    val relativeResourcePath = requestorResourcePath.substring(projectName.length() + 1)
    val referencedFile: VirtualFile?
    val referencedProject: Project?

    val accessToken = ReadAction.start()

    try {
      if (relativeResourcePath.startsWith("classpath:/")) {
        val typeName = relativeResourcePath.substring("classpath:/".length())
        referencedFile = JarFileSystem.getInstance().findFileByPath(typeName)
        referencedProject = findReferencedProject(projectName)
      }
      else {
        referencedFile = findReferencedFile(relativeResourcePath, projectName)
        if (referencedFile == null) {
          return false
        }

        referencedProject = findReferencedProject(referencedFile)
      }

      if (referencedFile == null || referencedProject == null) {
        return false
      }

      val document = FileDocumentManager.getInstance().getDocument(referencedFile)
      if (document == null) {
        return false
      }

      val resolve = getTargetElement(offset, referencedProject, document, false)
      if (resolve == null) {
        return false
      }

      writer.name("navigation").beginObject()
      writer.name("project").value(projectName)
      val containingFile = resolve.getContainingFile()

      var virtualFile = containingFile.getVirtualFile() ?: containingFile.getOriginalFile().getVirtualFile()
      // todo jar
      writer.name("resource")
      if (virtualFile!!.isInLocalFileSystem()) {
        writer.value(VfsUtilCore.getRelativePath(virtualFile, referencedProject.getBaseDir()))
      }
      else {
        writer.value("classpath:/${virtualFile!!.getPath()}")
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
      writer.name("offset").value(textRange!!.getStartOffset())
      writer.name("length").value(textRange!!.getLength())
      return true
    }
    finally {
      accessToken.finish()
    }
  }
}