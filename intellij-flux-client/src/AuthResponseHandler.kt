package org.intellij.flux

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.io.webSocket.WebSocketHandshakeHandler
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.util.UUID

public data class Credential(val id: String, val token: String)

class AuthResponseHandler : HttpRequestHandler() {
  companion object {
    private val LOG = Logger.getInstance(javaClass<WebSocketHandshakeHandler>())

    fun requestAuth(host: String, project: Project?): Promise<Credential> {
      val userId = System.getProperty("flux.user.name")
      if (userId != null) {
        val token = System.getProperty("flux.user.token")
        if (token != null) {
          return Promise.resolve(Credential(userId, token))
        }
      }

      val responseHandler = HttpRequestHandler.EP_NAME.findExtension(javaClass<AuthResponseHandler>())
      val requestId = UUID.randomUUID().toString()
      val promise = AsyncPromise<Credential>()
      responseHandler.idToPromise.put(requestId, promise)
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Waiting authentication response from browser", false, PerformInBackgroundOption.DEAF) {
        override fun run(indicator: ProgressIndicator) {
          BrowserUtil.open("$host/auth/ide.html?r=$requestId&port=${BuiltInServerManager.getInstance().waitForStart().getPort()}")

          while (promise.state == Promise.State.PENDING) {
            try {
              //noinspection BusyWait
              Thread.sleep(500)
            }
            catch (ignored: InterruptedException) {
              break
            }

            if (indicator.isCanceled()) {
              LOG.warn("Response waiting canceled")
              promise.setError(Promise.createError("canceled"))
              break
            }
          }
        }
      })
      return promise
    }
  }

  private val idToPromise = ContainerUtil.newConcurrentMap<String, AsyncPromise<Credential>>()

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
      promise.setResult(Credential(user, token))
    }
    return true
  }
}