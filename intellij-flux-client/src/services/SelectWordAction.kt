package org.intellij.flux

import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.lang.CompositeLanguage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.util.Processor
import org.jetbrains.flux.EditorServiceBase
import org.jetbrains.flux.Result

private fun _findElementAt(file: PsiFile, caretOffset: Int): PsiElement? {
  val elementAt = file.findElementAt(caretOffset)
  if (elementAt != null && isLanguageExtension(file, elementAt)) {
    return file.getViewProvider().findElementAt(caretOffset, file.getLanguage())
  }
  return elementAt
}

private fun isLanguageExtension(file: PsiFile, elementAt: PsiElement): Boolean {
  val language = file.getLanguage()
  if (language is CompositeLanguage) {
    val elementLanguage = elementAt.getLanguage()
    for (extension in language.getLanguageExtensionsForFile(file)) {
      if (extension == elementLanguage) {
        return true
      }
    }
  }
  return false
}

trait SelectWordAction : EditorServiceBase {
  companion object {
  }

  override fun selectWord(projectName: String, resourcePath: String, offset: Int, selectionStart: Int, selectionEnd: Int, result: Result) {
    val file = findReferencedFile(resourcePath, projectName)
    val project = file?.findProject()

    val token = ReadAction.start()
    val range: TextRange?
    val document: Document?
    try {
      document = file?.getDocument()
      if (document == null) {
        range = null
      }
      else {
        val psiFile = PsiDocumentManager.getInstance(project!!).getPsiFile(document)
        if (psiFile == null) {
          result.empty()
          return
        }

        var caretOffset = offset
        if (caretOffset != 0) {
          val text = document.getImmutableCharSequence()
          if ((caretOffset < text.length() && !Character.isJavaIdentifierPart(text.charAt(caretOffset)) && Character.isJavaIdentifierPart(text.charAt(caretOffset - 1))) ||
            ((caretOffset == text.length() || Character.isWhitespace(text.charAt(caretOffset))) && !Character.isWhitespace(text.charAt(caretOffset - 1)))) {
            caretOffset -= 1
          }
        }

        range = doSelectWord(psiFile, caretOffset, document, project, selectionStart, selectionEnd)
      }
    }
    finally {
      token.finish()
    }

    if (document == null) {
      result.notFound()
    }
    else if (range == null) {
      result.empty()
    }
    else {
      result.array {
        int(range.getStartOffset())
        int(range.getEndOffset())
      }
    }
  }

  private fun doSelectWord(file: PsiFile, offset: Int, document: Document, project: Project, selectionStart: Int, selectionEnd: Int): TextRange? {
    var caretOffset = offset
    var element = _findElementAt(file, caretOffset)
    _findElementAt(file, caretOffset)
    if (element is PsiWhiteSpace && caretOffset > 0) {
      val anotherElement = _findElementAt(file, caretOffset - 1)
      if (anotherElement !is PsiWhiteSpace) {
        element = anotherElement
      }
    }

    while ((element is PsiWhiteSpace || element != null) && StringUtil.isEmptyOrSpaces(element.getText())) {
      while (element!!.getNextSibling() == null) {
        if (element is PsiFile) {
          return null
        }

        val parent = element.getParent()
        val children = parent.getChildren()
        if (children.size() > 0 && children[children.size() - 1] == element) {
          element = parent
        }
        else {
          element = parent
          break
        }
      }

      element = element.getNextSibling()
      if (element == null) {
        return null
      }

      val range = element.getTextRange()
      if (range == null) {
        return null
      }

      caretOffset = range.getStartOffset()
    }

    if (element is OuterLanguageElement) {
      var elementInOtherTree = file.getViewProvider().findElementAt(element.getTextOffset(), element.getLanguage())
      if (elementInOtherTree == null || elementInOtherTree.getContainingFile() != element.getContainingFile()) {
        while (elementInOtherTree != null && elementInOtherTree.getPrevSibling() == null) {
          elementInOtherTree = elementInOtherTree.getParent()
        }

        if (elementInOtherTree != null) {
          assert(elementInOtherTree.getTextOffset() == caretOffset)
          element = elementInOtherTree
        }
      }
    }

    if (element != null && element.getTextRange().getEndOffset() > document.getTextLength()) {
      LOG.warn("Wrong element range " + element + "; committed=" + PsiDocumentManager.getInstance(project).isCommitted(document))
    }

    val selectionRange = TextRange(selectionStart, selectionEnd)
    var minimumRange = TextRange(0, document.getTextLength())
    SelectWordUtil.processRanges(element, document.getCharsSequence(), caretOffset, FakeEditor(document, project), object : Processor<TextRange> {
      override fun process(range: TextRange): Boolean {
        if (range.contains(selectionRange) && range != selectionRange) {
          if (minimumRange.contains(range)) {
            minimumRange = range
            return true
          }
        }
        return false
      }
    })
    return minimumRange
  }
}