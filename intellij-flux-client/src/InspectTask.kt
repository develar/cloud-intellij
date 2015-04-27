package org.intellij.flux

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.update.Update
import org.jetbrains.flux.EditorTopics
import org.jetbrains.flux.Result
import org.jetbrains.json.ArrayMemberWriter
import org.jetbrains.json.JsonWriterEx
import java.util.Collections
import java.util.Comparator

class InspectTask(private val project: Project,
                       private val document: Document,
                       private val result: Result? = null,
                       private val resourcePath: String? = null) : Update(document, if (result == null) Update.LOW_PRIORITY else Update.HIGH_PRIORITY) {
  override fun run() {
    try {
      val error = inspect()
      if (error != null) {
        result?.reject(error)
      }
    }
    catch (e: Throwable) {
      result?.reject(e)
    }
  }

  private fun inspect(): String? {
    val messageConnector = fluxManager.messageConnector
    if (messageConnector == null) {
      return "messageConnector null"
    }

    val writer = JsonWriterEx()
    val indicator = DaemonProgressIndicator()
    var error: String? = null
    ProgressManager.getInstance().runProcess(Runnable {
      val accessToken = ReadAction.start()
      try {
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)
        if (psiFile == null) {
          error = "psiFile null"
        }
        else {
          val passes = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project).instantiateMainPasses(psiFile, document, HighlightInfoProcessor.getEmpty())
          Collections.sort(passes, object : Comparator<TextEditorHighlightingPass> {
            override fun compare(o1: TextEditorHighlightingPass, o2: TextEditorHighlightingPass): Int {
              if (o1 is GeneralHighlightingPass) return -1
              if (o2 is GeneralHighlightingPass) return 1
              return 0
            }
          })

          writer.map {
            if (resourcePath != null) {
              "project"(project.getName())
              "path"(resourcePath)
            }

            array("problems") {
              for (pass in passes) {
                pass.doCollectInformation(indicator)
                for (info in pass.getInfos()) {
                  writeInfos(info)
                }
              }
            }
          }
        }
      }
      finally {
        accessToken.finish()
      }
    }, indicator)

    if (error != null) {
      return error
    }

    val bytes = writer.toByteArray()
    if (result == null) {
      messageConnector.notify(EditorTopics.metadataChanged, bytes)
    }
    else {
      result.write(bytes)
    }
    return null
  }

  private fun ArrayMemberWriter.writeInfos(marker: HighlightInfo) {
    if (marker.getDescription() == null) {
      return
    }

    map {
      "description"(marker.getDescription())
      "severity"(when (marker.getSeverity()) {
        HighlightSeverity.ERROR -> "error"
        HighlightSeverity.INFORMATION -> "task"
        else -> "warning"
      })
      "start"(marker.getStartOffset())
      "end"(marker.getEndOffset())
      map("rangeStyle") {
        var styleClass = when (marker.type.getAttributesKey()) {
          CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES -> "unknownSymbol"
          CodeInsightColors.ERRORS_ATTRIBUTES -> "error"
          CodeInsightColors.WARNINGS_ATTRIBUTES -> "warning"
          else -> null
        }

        if (styleClass != null) {
          "styleClass"("annotationRange $styleClass")
        }
      }
    }
  }

  override fun canEat(update: Update?): Boolean {
    if ((update as InspectTask).document != document) {
      return false
    }
    if ((update.result == null && result == null) || (update.result == null && result != null)) {
      return true
    }
    return false
  }
}