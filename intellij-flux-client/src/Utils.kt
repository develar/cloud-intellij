package org.intellij.flux

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.codec.binary.Hex
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest

public inline fun StringBuilder.plusAssign(s: String) {
  append(s)
}

public inline fun StringBuilder.plusAssign(s: Char) {
  append(s)
}

fun CharSequence.toByteBuffer(): ByteBuffer {
  return Charsets.UTF_8.encode(CharBuffer.wrap(this))
}

fun CharSequence.sha1(): String {
  val digest = MessageDigest.getInstance("SHA-1")
  digest.update(toByteBuffer())
  return Hex.encodeHexString(digest.digest())
}

fun findReferencedProject(projectName: String): Project? {
  for (project in ProjectManager.getInstance().getOpenProjects()) {
    if (project.getName() == projectName) {
      return project
    }
  }
  return null
}

fun VirtualFile.getDocument() = FileDocumentManager.getInstance().getDocument(this)

fun Project.findFile(resourcePath: String) = findReferencedFile(resourcePath, this)

fun findReferencedFile(resourcePath: String, projectName: String): VirtualFile? {
  val project = findReferencedProject(projectName)
  return if (project == null) null else findReferencedFile(resourcePath, project)
}

fun findReferencedFile(path: String, project: Project) = project.getBaseDir().findFileByRelativePath(path)

fun VirtualFile.findProject(): Project? {
  for (project in ProjectManager.getInstance().getOpenProjects()) {
    if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(this)) {
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