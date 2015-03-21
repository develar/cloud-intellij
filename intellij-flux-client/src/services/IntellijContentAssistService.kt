package org.intellij.flux

import com.google.gson.stream.JsonWriter
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
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.ContentAssistService
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

class IntellijContentAssistService() : ContentAssistService {
  override fun get(projectName: String, resourcePath: String, offset: Int, prefix: String, result: Result) {
    result.writeIf {
      it.name("project").value(projectName)
      it.name("resource").value(resourcePath)
      it.name("proposals")
      computeContentAssist(projectName, resourcePath, offset, prefix, it)
    }
  }

  protected fun computeContentAssist(projectName: String, resourcePath: String, offset: Int, prefix: String, writer: JsonWriter): Boolean {
    writer.beginArray()
    val referencedFile = findReferencedFile(resourcePath, projectName)
    if (referencedFile == null) {
      writer.endArray()
      return false
    }

    val referencedProject = findReferencedProject(referencedFile)
    if (referencedProject == null) {
      writer.endArray()
      return false
    }

    var items: Array<LookupElement> = LookupElement.EMPTY_ARRAY
    ApplicationManager.getApplication().invokeAndWait(object : Runnable {
      override fun run() {
        val editor = FileEditorManager.getInstance(referencedProject).openTextEditor(OpenFileDescriptor(referencedProject, referencedFile, offset), false)
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
        LookupManager.getInstance(referencedProject).addPropertyChangeListener(propertyChangeListener)
        CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(referencedProject, editor)
        LookupManager.getInstance(referencedProject).removePropertyChangeListener(propertyChangeListener)
        val lookup = lookupRef.get()
        items = if (lookup == null) LookupElement.EMPTY_ARRAY else lookup.getItems().copyToArray()
      }
    }, ModalityState.any())

    val accessToken = ReadAction.start()
    try {
      for (proposal in items) {
        writer.beginObject()
        writer.name("proposal").value(getCompletion(proposal, prefix))
        writeDescription(proposal, writer)
        writePositions(proposal, prefix, offset, writer)
        writer.name("replace").value(true)
        writer.endObject()
      }
    }
    finally {
      accessToken.finish()
    }

    writer.endArray()
    return true
  }

  private fun writePositions(proposal: LookupElement, prefix: String, globalOffset: Int, writer: JsonWriter) {
    if (proposal.getPsiElement() !is PsiMethod) {
      return
    }

    var completion = proposal.getLookupString()
    if (completion.startsWith(prefix)) {
      completion = completion.substring(prefix.length())
    }

    writer.name("positions").beginArray()

    completion += "()"
    val parameters = (proposal.getPsiElement() as PsiMethod).getParameterList().getParameters()
    if (!parameters.isEmpty()) {
      var offset = globalOffset
      offset += completion.length() - 1

      for (i in parameters.indices) {
        writer.beginObject()
        writer.name("offset").value(offset)
        writer.name("length").value(parameters[i].getName().length())
        writer.endObject()

        offset += parameters[i].getName().length()
        offset += ", ".length()
      }
    }

    writer.endArray()
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

  inline
  private fun describe(icon: String, writer: JsonWriter, f: () -> Unit) {
    writer.name("description").beginObject()
    writer.name("icon").beginObject().name("src").value("../js/editor/textview/$icon").endObject()
    writer.name("segments").beginArray()
    writer.beginObject()
    f()
  }

  // method name completion starts live template ?
  private fun writeDescription(proposal: LookupElement, writer: JsonWriter) {
    val psiElement = proposal.getPsiElement()
    if (psiElement is PsiMethod) {
      describe("methpub_obj.gif", writer) {
        val parameters = psiElement.getParameterList().getParameters()
        var sig = psiElement.getName() + "("
        for (i in parameters.indices) {
          if (i != 0) {
            sig += ", "
          }
          sig += parameters[i].getType().getPresentableText()
          sig += " "
          sig += parameters[i].getName()
        }
        sig += ")"

        val returnType = psiElement.getReturnType()
        writer.name("value").value(sig + " : " + (if (returnType != null) returnType.getPresentableText() else "unknown"))
        writer.endObject()

        writer.beginObject()
        writer.name("value").value(" - " + psiElement.getContainingClass()!!.getName())
      }
    }
    else if (psiElement is PsiField) {
      describe("field_public_obj.gif", writer) {
        writer.name("value").value(psiElement.getName()!! + " : " + psiElement.getType().getPresentableText())
        writer.endObject()

        writer.beginObject()
        writer.name("value").value(" - " + psiElement.getContainingClass()!!.getName())
      }
    }
    else if (psiElement is PsiClass) {
      describe("class_obj.gif", writer) {
        writer.name("value").value(psiElement.getName())
        writer.endObject()

        writer.beginObject()
        val qualifiedName = (psiElement).getQualifiedName()
        writer.name("value").value(" - " + (if (qualifiedName != null) StringUtil.getPackageName(qualifiedName) else "unknown"))
      }
    }
    else {
      return
    }

    writer.name("style").beginObject().name("color").value("#AAAAAA").endObject()
    writer.endObject()

    writer.endArray()

    writer.endObject()
    writer.name("style").value("attributedString")
  }
}