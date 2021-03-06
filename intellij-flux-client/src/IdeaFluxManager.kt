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
  private var session: Session? = null

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
    if (PropertiesComponent.getInstance().getBoolean("flux.auto.connect", false)) {
      connect()
    }
  }

  fun isLoggedIn() = session != null

  fun logOut() {
    session = null
  }

  fun connect(project: Project? = null): Promise<RabbitMqMessageConnector> {
    val promise = AsyncPromise<RabbitMqMessageConnector>()
    connectPromise = promise

    val host = fluxHost
//    val refreshTokenAndUserId = keychain.get(host)
    if (session == null) {
      login(host, project)
        .done {
          session = it
          keychain.save(host, Credentials(it.userId, it.refreshToken))
          doConnect(it.userId, it.accessToken, promise)
        }
        .rejected {
          connectPromise = null
          promise.setError(it)
        }
    }
    else {
      doConnect(session!!.userId, session!!.accessToken, promise)
    }
    return promise
  }

  private fun doConnect(user: String, token: String, promise: AsyncPromise<RabbitMqMessageConnector>) {
    ApplicationManager.getApplication().executeOnPooledThread {
      fluxService.connect(user, token, promise)
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