package org.intellij.flux

import com.google.gson.stream.JsonWriter
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.RenameService

class IntellijRenameService() : RenameService {
  override fun renameInFile(request: Map<String, Any>, result: Result) {
    val projectName = request.get("project") as String
    val resourcePath = request.get("resource") as String
    result.writeIf {
      if (computeReferences("$projectName/$resourcePath", request.get("offset") as Int, it)) {
        it.name("project").value(projectName)
        it.name("resource").value(resourcePath)
        true
      }
      else {
        false
      }
    }
  }

  private fun computeReferences(resourcePath: String, offset: Int, writer: JsonWriter): Boolean {
    val projectName = resourcePath.substring(0, resourcePath.indexOf('/'))
    val relativeResourcePath = resourcePath.substring(projectName.length() + 1)
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

      val resolve = getTargetElement(offset, referencedProject, document, true)
      // only local symbols now
      if (resolve == null || !referencedFile.equals(resolve.getContainingFile().getVirtualFile())) {
        return false
      }

      writer.name("references").beginArray()
      var nameRefOffset = -1

      if (resolve is PsiNameIdentifierOwner) {
        val nameIdentifier = resolve.getNameIdentifier()
        if (nameIdentifier != null) {
          writer.beginObject()
          val textRange = nameIdentifier.getTextRange()
          nameRefOffset = textRange.getStartOffset()
          writer.name("offset").value(nameRefOffset)
          writer.name("length").value(textRange.getLength())
        }
      }

      for (ref in ReferencesSearch.search(resolve, LocalSearchScope(resolve.getContainingFile())).findAll()) {
        val elementRange = ref.getElement().getTextRange()
        val rangeInElement = ref.getRangeInElement()
        val refOffset = elementRange.getStartOffset() + rangeInElement.getStartOffset()
        if (refOffset == nameRefOffset) {
          continue
        }
        writer.beginObject()
        writer.name("offset").value(refOffset)
        writer.name("length").value(rangeInElement.getLength())
      }

      writer.endArray()
      return true
    }
    finally {
      accessToken.finish()
    }
  }
}