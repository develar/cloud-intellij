package org.intellij.flux

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.BuiltInServerManager
import org.jetbrains.ide.HttpRequestHandler
import org.jetbrains.io.ChannelBufferToString
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.keychain.Credentials
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.util.UUID

fun login(host: String, project: Project?): Promise<Credentials> {
  return HttpRequestHandler.EP_NAME.findExtension(javaClass<AuthResponseHandler>()).requestAuth(host, project)
}

class AuthResponseHandler : HttpRequestHandler() {
  private val idToPromise = ContainerUtil.newConcurrentMap<String, AsyncPromise<Credentials>>()

  fun requestAuth(host: String, project: Project?): Promise<Credentials> {
    val promise = AsyncPromise<Credentials>()
    val requestId = UUID.randomUUID().toString()
    idToPromise.put(requestId, promise)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Waiting authentication response from browser", true, PerformInBackgroundOption.DEAF) {
      override fun onCancel() {
        cancelPromise()
      }

      override fun run(indicator: ProgressIndicator) {
        BrowserUtil.open("https://$host/ide-auth.html?r=$requestId&port=${BuiltInServerManager.getInstance().waitForStart().getPort()}")

        while (promise.state == Promise.State.PENDING) {
          try {
            //noinspection BusyWait
            Thread.sleep(500)
          }
          catch (ignored: InterruptedException) {
            break
          }

          if (indicator.isCanceled()) {
            cancelPromise()
            break
          }
        }
      }

      private fun cancelPromise() {
        try {
          idToPromise.remove(requestId)
          LOG.warn("Response waiting canceled")
        }
        finally {
          promise.setError(Promise.createError("canceled"))
        }
      }
    })
    return promise
  }

  override fun isSupported(request: FullHttpRequest): Boolean {
    return request.method() == HttpMethod.POST && HttpRequestHandler.checkPrefix(request.uri(), "67822818-87E4-4FF9-81C5-75433D57E7B3")
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val reader = JsonReaderEx(ChannelBufferToString.readChars(request.content()))
    reader.beginArray()
    val requestId = reader.nextString()
    val user = reader.nextString()
    val token = reader.nextString()
    reader.endArray()

    val promise = idToPromise.remove(requestId)
    if (promise == null) {
      LOG.warn("No request for id $requestId")
    }
    else {
      promise.setResult(Credentials(user, token))
    }
    return true
  }
}