package org.intellij.flux

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.LiveEditTopics
import org.eclipse.flux.client.MessageConnector
import org.eclipse.flux.client.services.LiveEditService

private val CHANGE_FLAG = Key<Boolean>("our.change")

class IntellijLiveEditService(messageConnector: MessageConnector, private val errorAnalyzerService: ErrorAnalyzerService) : LiveEditService(messageConnector) {
  override fun liveEditors(replyTo: String, correlationId: String, projectRegEx: String?, resourceRegEx: String?, liveUnits: List<Map<String, Any>>) {
    [suppress("UNCHECKED_CAST")]
    for (liveUnit in liveUnits) {
      [suppress("NAME_SHADOWING")]
      val projectName = liveUnit.get("project") as String
      val resource = liveUnit.get("resource") as String
      val timestamp = liveUnit.get("savePointTimestamp") as Long
      val hash = liveUnit.get("savePointHash") as String

      startLiveUnit(replyTo, correlationId, projectName, resource, hash, timestamp)

      sendLiveEditStartedMessage(projectName, resource, hash, timestamp)
    }
  }

  override fun liveEditingStarted(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, timestamp: Long) {
    startLiveUnit(replyTo, correlationId, projectName, resourcePath, hash, timestamp)
  }

  override fun liveEditingStartedResponse(projectName: String, resourcePath: String, savePointHash: String, savePointTimestamp: Long, content: String) {
    val document = findReferencedFile(resourcePath, projectName)?.getDocument()
    if (document == null) {
      return
    }

    val liveContent = document.getText()
    val liveUnitHash = DigestUtils.sha1Hex(liveContent)
    val remoteContentHash = DigestUtils.sha1Hex(content)
    if (liveUnitHash != remoteContentHash) {
      writeAction {
        document.putUserData<Boolean>(CHANGE_FLAG, java.lang.Boolean.TRUE)
        try {
          document.setText(content)
        }
        finally {
          document.putUserData<Boolean>(CHANGE_FLAG, null)
        }
      }
    }
  }

  override fun liveEditingEvent(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newText: String?) {
    val referencedFile = findReferencedFile(resourcePath, projectName)
    if (referencedFile != null) {
      writeAction {
        val document = FileDocumentManager.getInstance().getDocument(referencedFile)
        if (document != null) {
          CommandProcessor.getInstance().executeCommand({
            document.putUserData<Boolean>(CHANGE_FLAG, java.lang.Boolean.TRUE)
            try {
              document.replaceString(offset, offset + removeCount, newText ?: "")
            }
            finally {
              document.putUserData<Boolean>(CHANGE_FLAG, null)
            }
          }, "Edit", null)
        }
      }
    }
  }

  private fun startLiveUnit(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, timestamp: Long) {
    val accessToken = ReadAction.start()
    try {
      val referencedFile = findReferencedFile(resourcePath, projectName)
      val document = referencedFile?.getDocument()
      if (document == null) {
        return
      }

      val liveContent = document.getText()
      val liveUnitHash = DigestUtils.sha1Hex(liveContent)
      if (liveUnitHash == hash) {
        return
      }

      sendLiveEditStartedResponse(replyTo, correlationId, projectName, resourcePath, hash, timestamp, liveContent)
    }
    finally {
      accessToken.finish()
    }

    try {
      errorAnalyzerService.sendProblems(projectName, resourcePath, LiveEditTopics.liveMetadataChanged)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }
}