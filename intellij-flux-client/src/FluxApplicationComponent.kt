package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Time

class FluxApplicationComponent : ApplicationComponent {
  private var fluxService: IdeaFluxServiceManager? = null

  override public fun getComponentName() = "FluxConnector"

  public override fun initComponent() {
    ApplicationManager.getApplication().executeOnPooledThread {
      fluxService = IdeaFluxServiceManager(System.getProperty("flux.user", "dev"))
    }
  }

  override public fun disposeComponent() {
    fluxService?.messageConnector?.close(30 * Time.SECOND)
  }
}