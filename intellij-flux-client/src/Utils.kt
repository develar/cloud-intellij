package org.intellij.flux

import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

fun findReferencedProject(projectName: String): Project? {
  for (project in ProjectManager.getInstance().getOpenProjects()) {
    if (project.getName() == projectName) {
      return project
    }
  }
  return null
}

fun findReferencedFile(resourcePath: String, projectName: String): VirtualFile? {
  val project = findReferencedProject(projectName)
  return if (project == null) null else findReferencedFile(resourcePath, project)
}

fun findReferencedFile(resourcePath: String, project: Project): VirtualFile? {
  for (module in ModuleManager.getInstance(project).getModules()) {
    for (contentRoot in ModuleRootManager.getInstance(module).getContentRoots()) {
      val resource = VfsUtilCore.findRelativeFile(resourcePath, contentRoot!!)
      if (resource != null) {
        return resource
      }
    }
  }
  return null
}

fun findReferencedProject(file: VirtualFile): Project? {
  for (project in ProjectManager.getInstance().getOpenProjects()) {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file)) {
      return project
    }
  }
  return null
}

fun getTargetElement(offset: Int, referencedProject: Project, document: Document, nameElementAccepted: Boolean): PsiElement? {
  var psiFile = PsiDocumentManager.getInstance(referencedProject).getPsiFile(document)
  if (psiFile is PsiCompiledFile) psiFile = (psiFile as PsiCompiledFile).getDecompiledPsiFile()
  val referenceAt = if (psiFile != null) psiFile!!.findReferenceAt(offset) else null
  if (referenceAt == null) {
    if (nameElementAccepted && psiFile != null) {
      val parent = PsiTreeUtil.getParentOfType(psiFile!!.findElementAt(offset), javaClass<PsiNamedElement>())
      if (parent != null) return parent
    }
    return null
  }
  var resolve = referenceAt.resolve()
  if (resolve == null) {
    if (referenceAt is PsiPolyVariantReference) {
      val resolveResults = referenceAt.multiResolve(false)
      if (resolveResults.size() == 0) {
        return null
      }
      // todo multiple variants
      resolve = resolveResults[0].getElement()
      if (resolve == null) {
        return null
      }
    }
    else {
      return null
    }
  }
  return resolve
}