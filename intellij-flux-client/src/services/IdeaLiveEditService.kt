package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
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

  override fun changed(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newFragment: String?, replyTo: String, correlationId: String) {
    val project = findReferencedProject(projectName)
    val file = project?.findFile(resourcePath)
    if (file == null) {
      return
    }

    val document = file.getDocument()
    if (document == null) {
      return
    }

    ApplicationManager.getApplication().invokeLater({
      CommandProcessor.getInstance().executeCommand(project!!, {
        val token = WriteAction.start()
        try {
          document.putUserData<Boolean>(CHANGE_FLAG, java.lang.Boolean.TRUE)
          try {
            if (newFragment.isNullOrEmpty()) {
              document.deleteString(offset, offset + removeCount)
            }
            else {
              document.replaceString(offset, offset + removeCount, newFragment!!)
            }
          }
          finally {
            document.putUserData<Boolean>(CHANGE_FLAG, null)
          }
        }
        finally {
          token.finish()
        }

        highlighterService(project).sendHighlighting(document, file, resourcePath, replyTo, correlationId, offset, newFragment?.length() ?: 0, messageConnector)

//        messageConnector.notify(EditorTopics.metadataChanged) {
//          computeProblems(document, project, projectName, resourcePath)
//        }
      }, "Edit", null)
    }, ModalityState.any())
  }

  /**
   * If requestorHash specified, so, requestor wants to get actual content if differs.
   * If not specified, so, requestor has actual content and we don't need to send it in any case.
   */
  override fun started(projectName: String, resourcePath: String, requestorHash: String?, replyTo: String, correlationId: String) {
    val project = findReferencedProject(projectName)
    if (project == null) {
      return
    }

    val content: CharSequence?
    val document: Document?
    val file: VirtualFile?
    val accessToken = ReadAction.start()
    try {
      file = project.findFile(resourcePath)
      document = file?.getDocument()
      if (document == null) {
        return
      }

      content = if (requestorHash == null) null else document.getImmutableCharSequence()
    }
    finally {
      accessToken.finish()
    }

    if (requestorHash != null) {
      val hash = content!!.sha1()
      if (hash != requestorHash) {
        sendStartedResponse(replyTo, correlationId, projectName, resourcePath, hash, content)
      }
    }

    highlighterService(project).sendHighlighting(document!!, file!!, resourcePath, replyTo, correlationId, 0, document.getTextLength(), messageConnector)
  }
}