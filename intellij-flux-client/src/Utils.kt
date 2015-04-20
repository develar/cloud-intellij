package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.apache.commons.codec.binary.Hex
import java.awt.Color
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest

fun Color.toHex() = java.lang.String.format("#%06X", 0xFFFFFF and this.getRGB())

public fun StringBuilder.plusAssign(s: String) {
  append(s)
}

public fun StringBuilder.plusAssign(s: Char) {
  append(s)
}

inline fun writeAction(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) task: () -> Unit): Unit {
  ApplicationManager.getApplication().invokeLater(Runnable {
    val token = WriteAction.start()
    try {
      task()
    }
    finally {
      token.finish()
    }
  })


}

inline fun readAction(task: () -> Unit): Unit {
  val token = ReadAction.start()
  try {
    task()
  }
  finally {
    token.finish()
  }
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

fun VirtualFile.getDocument(): Document? {
  val document: Document?
  val token = ReadAction.start()
  try {
    document = FileDocumentManager.getInstance().getDocument(this)
  }
  finally {
    token.finish()
  }
  return document
}

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
  if (psiFile is PsiCompiledFile) psiFile = psiFile.getDecompiledPsiFile()
  val referenceAt = if (psiFile != null) psiFile.findReferenceAt(offset) else null
  if (referenceAt == null) {
    if (nameElementAccepted && psiFile != null) {
      val parent = PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), javaClass<PsiNamedElement>())
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