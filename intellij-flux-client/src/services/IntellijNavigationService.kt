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
import org.eclipse.flux.client.services.NavigationServiceBase

class IntellijNavigationService() : NavigationServiceBase {
  override fun computeNavigation(projectName: String, resourcePath: String, offset: Int, writer: JsonWriter): Boolean {
    val referencedFile: VirtualFile?
    val referencedProject: Project?

    val accessToken = ReadAction.start()

    try {
      if (resourcePath.startsWith("classpath:/")) {
        val typeName = resourcePath.substring("classpath:/".length())
        referencedFile = JarFileSystem.getInstance().findFileByPath(typeName)
        referencedProject = findReferencedProject(projectName)
      }
      else {
        referencedFile = findReferencedFile(resourcePath, projectName)
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