package org.intellij.flux

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
import org.jetbrains.json.MapMemberWriter

class IdeaRenameService() : RenameService {
  override fun renameInFile(projectName: String, resourcePath: String, offset: Int, result: Result) {
    result.map {
      if (!computeReferences(projectName, resourcePath, offset)) {
        "error"(404)
      }
    }
  }

  private fun MapMemberWriter.computeReferences(projectName: String, resourcePath: String, offset: Int): Boolean {
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
        referencedProject = referencedFile?.findProject()
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

      array("references") {
        var nameRefOffset = -1
        if (resolve is PsiNameIdentifierOwner) {
          val nameIdentifier = resolve.getNameIdentifier()
          if (nameIdentifier != null) {
            map {
              val textRange = nameIdentifier.getTextRange()
              nameRefOffset = textRange.getStartOffset()
              "offset"(nameRefOffset)
              "length"(textRange.getLength())
            }
          }
        }

        for (ref in ReferencesSearch.search(resolve, LocalSearchScope(resolve.getContainingFile())).findAll()) {
          val elementRange = ref.getElement().getTextRange()
          val rangeInElement = ref.getRangeInElement()
          val refOffset = elementRange.getStartOffset() + rangeInElement.getStartOffset()
          if (refOffset == nameRefOffset) {
            continue
          }
          map {
            "offset"(refOffset)
            "length"(rangeInElement.getLength())
          }
        }
      }
      return true
    }
    finally {
      accessToken.finish()
    }
  }
}