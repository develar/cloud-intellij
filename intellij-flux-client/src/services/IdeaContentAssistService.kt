package org.intellij.flux

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.eclipse.flux.client.services.EditorServiceBase
import org.jetbrains.json.ArrayMemberWriter
import org.jetbrains.json.MapMemberWriter
import org.jetbrains.json.MessageWriter
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

trait IdeaContentAssistService : EditorServiceBase {
  override fun ArrayMemberWriter.computeContentAssist(projectName: String, resourcePath: String, offset: Int, prefix: String): Boolean {
    val file = findReferencedFile(resourcePath, projectName)
    val project = file?.findProject()
    if (project == null) {
      return false
    }

    var items: Array<LookupElement> = LookupElement.EMPTY_ARRAY
    ApplicationManager.getApplication().invokeAndWait(object : Runnable {
      override fun run() {
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file!!, offset), false)
        if (editor == null) {
          return
        }

        val lookupRef = Ref<LookupImpl>()
        val propertyChangeListener = object : PropertyChangeListener {
          override fun propertyChange(event: PropertyChangeEvent) {
            if ("activeLookup" == event.getPropertyName() && lookupRef.get() == null) {
              lookupRef.set(event.getNewValue() as LookupImpl)
            }
          }
        }
        LookupManager.getInstance(project).addPropertyChangeListener(propertyChangeListener)
        CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor)
        LookupManager.getInstance(project).removePropertyChangeListener(propertyChangeListener)
        val lookup = lookupRef.get()
        items = if (lookup == null) LookupElement.EMPTY_ARRAY else lookup.getItems().copyToArray()
      }
    }, ModalityState.any())

    val accessToken = ReadAction.start()
    try {
      for (proposal in items) {
        map {
          "proposal"(getCompletion(proposal, prefix))
          "overwrite"(true)
          writeDescription(proposal)
          writePositions(proposal, prefix, offset)
        }
      }
    }
    finally {
      accessToken.finish()
    }
    return true
  }

  private fun MapMemberWriter.writePositions(proposal: LookupElement, prefix: String, globalOffset: Int) {
    if (proposal.getPsiElement() !is PsiMethod) {
      return
    }

    var completion = proposal.getLookupString()
    if (completion.startsWith(prefix)) {
      completion = completion.substring(prefix.length())
    }

    array("positions") {
      completion += "()"
      val parameters = (proposal.getPsiElement() as PsiMethod).getParameterList().getParameters()
      if (!parameters.isEmpty()) {
        var offset = globalOffset
        offset += completion.length() - 1
        for (i in parameters.indices) {
          map {
            "offset"(offset)
            "length"(parameters[i].getName().length())
          }

          offset += parameters[i].getName().length()
          offset += ", ".length()
        }
      }
    }
  }

  private fun getCompletion(lookupElement: LookupElement, prefix: String): String {
    var completion = lookupElement.getLookupString()
    if (completion.startsWith(prefix)) {
      completion = completion.substring(prefix.length())
    }

    if (lookupElement.getPsiElement() is PsiMethod) {
      completion += "("
      val parameters = (lookupElement.getPsiElement() as PsiMethod).getParameterList().getParameters()
      if (parameters.size() > 0) {
        for (i in parameters.indices) {
          if (i > 0) {
            completion += ", "
          }
          completion += parameters[i].getName()
        }
      }
      completion += ")"
    }

    return completion
  }

  private inline fun MapMemberWriter.describe(icon: String, f: ArrayMemberWriter.() -> Unit) {
    map("description") {
      map("icon") {
        "src"("../js/editor/textview/$icon")
      }
      "style"("attributedString")
      array("segments") {
        f()
      }
    }
  }

  // method name completion starts live template ?
  private fun MapMemberWriter.writeDescription(proposal: LookupElement) {
    val psiElement = proposal.getPsiElement()
    if (psiElement is PsiMethod) {
      describe("methpub_obj.gif") {
        map {
          var sig = StringBuilder()
          for (parameter in psiElement.getParameterList().getParameters()) {
            if (sig.length() == 0) {
              sig.append(psiElement.getName()).append('(')
            }
            else {
              sig += ", "
            }
            sig += parameter.getType().getPresentableText()
            sig += ' '
            sig += parameter.getName()
          }
          sig += ')'

          val returnType = psiElement.getReturnType()
          sig += " : "
          sig += if (returnType != null) returnType.getPresentableText() else "unknown"
          "value"(sig)
        }
        map {
          "value"(" - " + psiElement.getContainingClass()!!.getName())
          map("style") {
            "color"("#AAAAAA")
          }
        }
      }
    }
    else if (psiElement is PsiField) {
      describe("field_public_obj.gif") {
        map {
          "value"(psiElement.getName()!! + " : " + psiElement.getType().getPresentableText())
        }

        map {
          "value"(" - " + psiElement.getContainingClass()!!.getName())
          map("style") {
            "color"("#AAAAAA")
          }
        }
      }
    }
    else if (psiElement is PsiClass) {
      describe("class_obj.gif") {
        map {
          "value"(psiElement.getName())
        }
        map {
          val qualifiedName = (psiElement).getQualifiedName()
          "value"(" - " + (if (qualifiedName != null) StringUtil.getPackageName(qualifiedName) else "unknown"))
          map("style") {
            "color"("#AAAAAA")
          }
        }
      }
    }
  }
}