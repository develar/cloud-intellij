package org.intellij.flux

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import com.intellij.util.Time
import org.jetbrains.flux.MessageConnector
import org.jetbrains.flux.RabbitMqMessageConnector
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise

val fluxManager: IdeaFluxManager
  get() = ApplicationManager.getApplication().getComponent(javaClass<IdeaFluxManager>())

class IdeaFluxManager : ApplicationComponent {
  private val fluxService = IdeaFluxServiceManager()

  override public fun getComponentName() = "FluxConnector"

  val messageConnector: MessageConnector?
    get() = fluxService.messageConnector

  private volatile var connectPromise: AsyncPromise<RabbitMqMessageConnector>? = null

  val connectionState: Promise.State?
    get() = connectPromise?.state

  public override fun initComponent() {
    if (PropertiesComponent.getInstance().getBoolean("flux.auto.connect", false)) {
      connect()
    }
  }

  fun connect(project: Project? = null): Promise<RabbitMqMessageConnector> {
    val promise = AsyncPromise<RabbitMqMessageConnector>()
    connectPromise = promise
    requestAuth("https://flux.dev", project)
      .done {
        ApplicationManager.getApplication().executeOnPooledThread {
          fluxService.connect(it.id, it.token, promise)
        }
      }
    return promise
  }

  override public fun disposeComponent() {
    disconnect()
  }

  fun disconnect() {
    val messageConnector = fluxService.messageConnector
    if (messageConnector != null) {
      connectPromise = null
      fluxService.messageConnector = null
      messageConnector.close(30 * Time.SECOND)
    }
  }
}