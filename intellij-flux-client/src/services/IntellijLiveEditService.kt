package org.intellij.flux

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.Key
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.EditorTopics
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
      val hash = liveUnit.get("savePointHash") as String

//      started(replyTo, correlationId, projectName, resource, hash)

//      startedMessage(projectName, resource, hash)
    }
  }

  override fun startedResponse(projectName: String, resourcePath: String, savePointHash: String, content: String) {
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

  override fun changed(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newText: String?) {
    val project = findReferencedProject(projectName)
    val referencedFile = if (project == null) null else findReferencedFile(resourcePath, project)
    if (referencedFile == null) {
      return
    }

    writeAction {
      val document = referencedFile.getDocument()
      if (document != null) {
        CommandProcessor.getInstance().executeCommand(project, {
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

  override fun started(replyTo: String, correlationId: String, projectName: String, resourcePath: String, requestorHash: String) {
    val content: String
    val accessToken = ReadAction.start()
    try {
      val referencedFile = findReferencedFile(resourcePath, projectName)
      val document = referencedFile?.getDocument()
      if (document == null) {
        return
      }

      content = document.getText()
      val hash = DigestUtils.sha1Hex(content)
      if (hash == requestorHash) {
        return
      }
    }
    finally {
      accessToken.finish()
    }

    sendStartedResponse(replyTo, correlationId, projectName, resourcePath, requestorHash, content)

    try {
      errorAnalyzerService.sendProblems(projectName, resourcePath, EditorTopics.metadataChanged)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}