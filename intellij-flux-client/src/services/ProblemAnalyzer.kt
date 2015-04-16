package org.intellij.flux

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.eclipse.flux.client.EditorTopics
import org.eclipse.flux.client.MessageConnector
import org.jetbrains.json.ArrayMemberWriter
import org.jetbrains.json.MapMemberWriter

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
      "path"(resourcePath)
    }

    array("problems") {
      // todo check is analyzing finished
      DaemonCodeAnalyzerEx.processHighlights(document, project, null, 0, document.getTextLength()) {
        writeInfos(it)
        true
      }
    }
  }
  finally {
    accessToken.finish()
  }
}

private fun ArrayMemberWriter.writeInfos(marker: HighlightInfo) {
  if (marker.getDescription() == null) {
    return
  }

  map {
    "description"(marker.getDescription())
    // default severity "error"
    if (marker.getSeverity() != HighlightSeverity.ERROR) {
      "severity"("warning")
    }
    "start"(marker.getStartOffset())
    "end"(marker.getEndOffset())
  }
}