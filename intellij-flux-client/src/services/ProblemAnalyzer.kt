package org.intellij.flux

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiDocumentManager
import org.eclipse.flux.client.MessageConnector
import org.eclipse.flux.client.Topic
import org.jetbrains.json.ArrayMemberWriter
import org.jetbrains.json.MapMemberWriter

fun MessageConnector.sendProblems(document: Document, project: Project, projectName: String, resourcePath: String, topic: Topic) {
  notify(topic) {
    computeProblems(document, project, projectName, resourcePath)
  }
}

/**
 * We should write project and resourcePath only if it is broadcast event response
 */
fun MapMemberWriter.computeProblems(document: Document, project: Project, projectName: String?, resourcePath: String?) {
  val accessToken = ReadAction.start()
  try {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
    if (psiFile == null) {
      return
    }

    if (projectName != null) {
      "project"(projectName)
      "resource"(resourcePath)
    }

    val pass = GeneralHighlightingPass(project, psiFile, document, 0, document.getTextLength(), false, ProperTextRange(0, document.getTextLength()), null, HighlightInfoProcessor.getEmpty())
    pass.collectInformation(DaemonProgressIndicator())

    array("problems") {
      writeInfos(pass.getInfos())
    }
  }
  finally {
    accessToken.finish()
  }
}

private fun ArrayMemberWriter.writeInfos(markers: List<HighlightInfo>) {
  for (m in markers) {
    if (m.getDescription() == null) {
      continue
    }

    map {
      "description"(m.getDescription())
      // default severity "error"
      if (m.getSeverity() != HighlightSeverity.ERROR) {
        "severity"("warning")
      }
      "start"(m.getStartOffset())
      "end"(m.getEndOffset())
    }
  }
}