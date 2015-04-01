package org.intellij.flux

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Time

inline fun writeAction(inlineOptions(InlineOption.ONLY_LOCAL_RETURN) task: () -> Unit): Unit {
  ApplicationManager.getApplication().invokeLater(Runnable {
    val token = WriteAction.start()
    try {
      task()
    }
    finally {
      token.finish()
    }
  })
}

inline fun readAction(task: () -> Unit): Unit {
  val token = ReadAction.start()
  try {
    task()
  }
  finally {
    token.finish()
  }
}

fun VirtualFile.getDocument() = FileDocumentManager.getInstance().getDocument(this)

class FluxApplicationComponent : ApplicationComponent {
  private var fluxService: FluxIntellijServiceManager? = null

  override public fun getComponentName() = "FluxConnector"

  public override fun initComponent() {
    ApplicationManager.getApplication().executeOnPooledThread {
      fluxService = FluxIntellijServiceManager(System.getProperty("flux.user", "dev"))
    }
  }

  override public fun disposeComponent() {
    fluxService?.messageConnector?.close(30 * Time.SECOND)
  }
}