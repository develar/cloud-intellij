package org.intellij.flux

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class LogOutFromFluxAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.getPresentation().setEnabledAndVisible(fluxManager.isLoggedIn())
  }

  override fun actionPerformed(e: AnActionEvent?) {
    try {
      fluxManager.disconnect()
    }
    finally {
      fluxManager.logOut()
    }
  }
}