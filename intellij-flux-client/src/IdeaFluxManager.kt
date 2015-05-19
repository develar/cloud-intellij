package org.intellij.flux

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import com.intellij.util.Time
import org.jetbrains.flux.MessageConnector
import org.jetbrains.flux.RabbitMqMessageConnector
import org.jetbrains.keychain.*
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.io.File
import kotlin.properties.Delegates

val fluxManager: IdeaFluxManager
  get() = ApplicationManager.getApplication().getComponent(javaClass<IdeaFluxManager>())

class IdeaFluxManager : ApplicationComponent {
  private val fluxService = IdeaFluxServiceManager()

  private val keychain: CredentialsStore by Delegates.lazy {
    if (isOSXCredentialsStoreSupported && SystemProperties.getBooleanProperty("use.osx.keychain", true)) {
      try {
        OsXCredentialsStore("IntelliJ Flux")
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
    FileCredentialsStore(File(PathManager.getConfigPath(), ".flux_auth"))
  }

  override public fun getComponentName() = "FluxConnector"

  val messageConnector: MessageConnector?
    get() = fluxService.messageConnector

  private volatile var connectPromise: AsyncPromise<RabbitMqMessageConnector>? = null

  val connectionState: Promise.State?
    get() = connectPromise?.state

  private val fluxHost = System.getProperty("flux.host", "intellij.io")

  public override fun initComponent() {
    val userId = System.getProperty("flux.user.name")
    if (userId != null) {
      val token = System.getProperty("flux.user.token")
      if (token != null) {
          keychain.save(fluxHost, Credentials(userId, token))
      }
    }

    if (PropertiesComponent.getInstance().getBoolean("flux.auto.connect", false)) {
      connect()
    }
  }

  fun connect(project: Project? = null): Promise<RabbitMqMessageConnector> {
    val promise = AsyncPromise<RabbitMqMessageConnector>()
    connectPromise = promise

    val host = fluxHost
    val credentials = keychain.get(host)
    if (credentials != null) {
      doConnect(credentials, promise)
    }
    else {
      login(host, project)
        .done {
          keychain.save(host, it)
          doConnect(it, promise)
        }
    }
    return promise
  }

  private fun doConnect(credentials: Credentials, promise: AsyncPromise<RabbitMqMessageConnector>) {
    ApplicationManager.getApplication().executeOnPooledThread {
      fluxService.connect(credentials.id!!, credentials.token!!, promise)
    }
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