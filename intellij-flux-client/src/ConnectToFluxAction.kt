package org.intellij.flux

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.util.concurrency.Promise

class ConnectToFluxAction : DumbAwareAction(FluxBundle.message(getActionText())) {
  override fun update(e: AnActionEvent) {
    val state = fluxManager.connectionState
    if (state == null) {
      e.getPresentation().setText(FluxBundle.message("connectTo"))
    }
    else if (state == Promise.State.FULFILLED) {
      e.getPresentation().setText(FluxBundle.message("disconnectFrom"))
    }
    else {
      e.getPresentation().setEnabled(false)
    }
  }

  override fun actionPerformed(e: AnActionEvent?) {
    if (fluxManager.messageConnector == null) {
      fluxManager.connect(e?.getProject())
      PropertiesComponent.getInstance().setValue("flux.auto.connect", "true", "false")
    }
    else {
      PropertiesComponent.getInstance().unsetValue("flux.auto.connect")
      fluxManager.disconnect()
    }
  }
}

private fun getActionText() = if (fluxManager.connectionState == null) "connectTo" else "disconnectFrom"