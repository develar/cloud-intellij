package org.intellij.flux

import com.intellij.openapi.project.Project
import org.eclipse.flux.client.ProjectTopics
import org.eclipse.flux.client.RabbitMqMessageConnector
import org.jetbrains.ide.PooledThreadExecutor

// todo change this username property to a preference and add authentication
class IdeaFluxServiceManager(username: String) {
  val messageConnector = RabbitMqMessageConnector(username, PooledThreadExecutor.INSTANCE, "idea-client")

  init {
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