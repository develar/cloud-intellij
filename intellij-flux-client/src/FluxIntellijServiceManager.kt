package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentAdapter
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.LiveEditTopics
import org.eclipse.flux.client.RabbitMqFluxConfig
import org.jetbrains.ide.PooledThreadExecutor

// todo change this username property to a preference and add authentication
class FluxIntellijServiceManager(userName: String) {
  val messageConnector = RabbitMqFluxConfig(userName).connect(PooledThreadExecutor.INSTANCE)

  private val repository = IntellijRepository(messageConnector, userName)
  private val errorAnalyzerService = ErrorAnalyzerService(messageConnector)
  private val liveEditService = IntellijLiveEditService(messageConnector, errorAnalyzerService)

  init {
    repository.addRepositoryListener(object : RepositoryListener {
      override fun projectConnected(project: Project) {
        messageConnector.notify(LiveEditTopics.liveResourcesRequested) {
          it.name("project").value(project.getName())
        }
      }
    })

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(object : DocumentAdapter() {
      override public fun documentChanged(documentEvent: DocumentEvent) {
        val document = documentEvent.getDocument()
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        if (virtualFile == null) return

        val referencedProject = findReferencedProject(virtualFile)
        // todo several projects
        if (referencedProject == null) return
        val resourcePath = VfsUtilCore.getRelativePath(virtualFile, referencedProject.getBaseDir())
        if (resourcePath == null) return

        if (document.getUserData(CHANGE_FLAG) == null) {
          val changeTimestamp = document.getModificationStamp() // todo
          //                    if (changeTimestamp > connectedProject.getTimestamp(resourcePath)) {
          val changeHash = DigestUtils.sha1Hex(document.getText())
          //                        if (!changeHash.equals(connectedProject.getHash(resourcePath))) {
          //
          ////                        connectedProject.setTimestamp(resourcePath, changeTimestamp);
          ////                        connectedProject.setHash(resourcePath, changeHash);

          liveEditService.sendModelChangedMessage(referencedProject.getName(), resourcePath, documentEvent.getOffset(), documentEvent.getOldLength(), documentEvent.getNewFragment().toString())
          //                        }
          //                    }
        }

        ApplicationManager.getApplication().invokeLater(Runnable {
          val token = WriteAction.start()
          try {
            PsiDocumentManager.getInstance(referencedProject).commitDocument(document)

          }
          finally {
            token.finish()
          }

          errorAnalyzerService.sendProblems(referencedProject.getName(), resourcePath, LiveEditTopics.liveMetadataChanged)
        })
      }
    })

    messageConnector.addService(IntellijNavigationService())
    messageConnector.addService(IntellijContentAssistService())
    messageConnector.addService(IntellijRenameService())
  }
}