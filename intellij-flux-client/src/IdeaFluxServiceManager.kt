package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.net.ssl.CertificateManager
import com.rabbitmq.client.Connection
import org.jetbrains.flux.ProjectTopics
import org.jetbrains.flux.RabbitMqMessageConnector
import org.jetbrains.flux.connectToMq
import org.jetbrains.ide.PooledThreadExecutor
import java.net.SocketException

// todo change this username property to a preference and add authentication
class IdeaFluxServiceManager() {
  var messageConnector: RabbitMqMessageConnector? = null

  fun connect(username: String, token: String) {
    val connection: Connection
    try {
      connection = doConnect(username, token)
    }
    catch (e: SocketException) {
      // "Connection reset" will be if server certificate is not trusted and CertificateManager trust manager display confirmation dialog
      ApplicationManager.getApplication().invokeLater(Runnable {
        init(username, doConnect(username, token))
      })
      return
    }

    init(username, connection)
  }

  private fun doConnect(username: String, token: String) = connectToMq(username, token, PooledThreadExecutor.INSTANCE, trustManager = CertificateManager.getInstance().getTrustManager())

  private fun init(username: String, connection: Connection) {
    val messageConnector = RabbitMqMessageConnector(username, "idea-client", connection)
    this.messageConnector = messageConnector

    IdeaLiveEditService(messageConnector)

    val repository = IdeaRepository(messageConnector, username)
    repository.addRepositoryListener(object : RepositoryListener {
      override fun projectConnected(project: Project) {
        messageConnector.notify(ProjectTopics.connected) {
          "project"(project.getName())
        }
      }
    })

    messageConnector.addService(IdeaEditorService())
    messageConnector.addService(IdeaRenameService())
  }
}