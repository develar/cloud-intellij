package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.util.Time
import org.jetbrains.flux.MessageConnector

val fluxManager: IdeaFluxManager
  get() = ApplicationManager.getApplication().getComponent(javaClass<IdeaFluxManager>())

class IdeaFluxManager : ApplicationComponent {
  private var fluxService: IdeaFluxServiceManager? = null

  override public fun getComponentName() = "FluxConnector"

  val messageConnector: MessageConnector?
    get() = fluxService?.messageConnector

    public override fun initComponent() {
    ApplicationManager.getApplication().executeOnPooledThread {
      fluxService = IdeaFluxServiceManager(System.getProperty("flux.user", "dev"))
    }
  }

  override public fun disposeComponent() {
    messageConnector?.close(30 * Time.SECOND)
  }
}