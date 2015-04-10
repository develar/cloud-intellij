package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.EditorTopics
import org.eclipse.flux.client.MessageConnector
import org.eclipse.flux.client.services.LiveEditService

private val CHANGE_FLAG = Key<Boolean>("our.change")

class IdeaLiveEditService(messageConnector: MessageConnector) : LiveEditService(messageConnector) {
  init {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(object : DocumentAdapter() {
      override public fun documentChanged(documentEvent: DocumentEvent) {
        val document = documentEvent.getDocument()
        if (document.getUserData(CHANGE_FLAG) != null) {
          return
        }

        val file = FileDocumentManager.getInstance().getFile(document)
        val referencedProject = file?.findProject()
        // todo several projects
        if (referencedProject == null) {
          return
        }

        val resourcePath = VfsUtilCore.getRelativePath(file!!, referencedProject.getBaseDir())
        if (resourcePath == null) {
          return
        }

        notifyChanged(referencedProject.getName(), resourcePath, documentEvent.getOffset(), documentEvent.getOldLength(), documentEvent.getNewFragment())

//        ApplicationManager.getApplication().invokeLater(Runnable {
//          val token = WriteAction.start()
//          try {
//            PsiDocumentManager.getInstance(referencedProject).commitDocument(document)
//          }
//          finally {
//            token.finish()
//          }
//
//          messageConnector.sendProblems(document, referencedProject, referencedProject.getName(), resourcePath, EditorTopics.metadataChanged)
//        })
      }
    })
  }

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
    val content: CharSequence
    val accessToken = ReadAction.start()
    val document: Document?
    val project: Project?
    try {
      project = findReferencedProject(projectName)
      document = project?.findFile(resourcePath)?.getDocument()
      if (document == null) {
        return
      }

      content = document.getImmutableCharSequence()
    }
    finally {
      accessToken.finish()
    }

    val hash = content.sha1()
    if (hash == requestorHash) {
      return
    }

    sendStartedResponse(replyTo, correlationId, projectName, resourcePath, hash, content)

//    try {
//      errorAnalyzerService.sendProblems(document!!, project!!, projectName, resourcePath, EditorTopics.metadataChanged)
//    }
//    catch (e: Throwable) {
//      LOG.error(e)
//    }
  }
}