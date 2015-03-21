package org.intellij.flux

import com.google.gson.stream.JsonWriter
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiDocumentManager
import org.eclipse.flux.client.MessageConnector
import org.eclipse.flux.client.Topic

class ErrorAnalyzerService(private val messageConnector: MessageConnector) {
  fun sendProblems(projectName: String, resourcePath: String, topic: Topic) {
    messageConnector.notify(topic) {
      val accessToken = ReadAction.start()
      try {
        //ConnectedProject connectedProject = this.syncedProjects.get(projectName);
        //                Module project = connectedProject.getProject();
        //                IResource resource = project.findMember(resourcePath);
        val file = findReferencedFile(resourcePath, projectName)
        val referencedProject = findReferencedProject(projectName)
        val document = file?.getDocument()
        if (referencedProject == null || document == null) {
          return
        }

        val psiFile = PsiDocumentManager.getInstance(referencedProject).getPsiFile(document)
        if (psiFile == null) {
          return
        }

        it.name("project").value(projectName)
        it.name("resource").value(resourcePath)
        it.name("type").value("marker")

        val pass = GeneralHighlightingPass(referencedProject, psiFile, document, 0, document.getTextLength(), false, ProperTextRange(0, document.getTextLength()), null, HighlightInfoProcessor.getEmpty())
        pass.collectInformation(DaemonProgressIndicator())

        it.name("problems")
        writeInfos(pass.getInfos(), document, it)
      }
      finally {
        accessToken.finish()
      }
    }
  }

  private fun writeInfos(markers: List<HighlightInfo>, document: Document, writer: JsonWriter) {
    writer.beginArray()
    for (m in markers) {
      if (m.getDescription() == null) {
        continue
      }

      writer.beginObject()
      writer.name("description").value(m.getDescription())
      writer.name("line").value(document.getLineNumber(m.getStartOffset()))
      writer.name("severity").value(if (m.getSeverity() == HighlightSeverity.ERROR) "error" else "warning")
      writer.name("start").value(m.getStartOffset())
      writer.name("end").value(m.getEndOffset())
      writer.endObject()
    }
    writer.endArray()
  }
}