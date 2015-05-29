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
import org.jetbrains.io.Responses
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.util.UUID

fun login(host: String, project: Project?): Promise<Session> {
  return HttpRequestHandler.EP_NAME.findExtension(javaClass<AuthResponseHandler>()).requestAuth(host, project)
}

class AuthResponseHandler : HttpRequestHandler() {
  private val idToPromise = ContainerUtil.newConcurrentMap<String, AsyncPromise<Session>>()

  fun requestAuth(host: String, project: Project?): Promise<Session> {
    val promise = AsyncPromise<Session>()
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
    return request.method() == HttpMethod.GET && HttpRequestHandler.checkPrefix(request.uri(), "67822818-87E4-4FF9-81C5-75433D57E7B3")
  }

  override fun process(urlDecoder: QueryStringDecoder, request: FullHttpRequest, context: ChannelHandlerContext): Boolean {
    val requestId = urlDecoder.getParameter("r")!!
    val promise = idToPromise.remove(requestId)
    if (promise == null) {
      LOG.warn("No request for id $requestId")
      failedAuth(context, request)
      return true
    }

    val error = urlDecoder.getParameter("e")
    if (error != null) {
      LOG.error(error)
      failedAuth(context, request)
      promise.setError(Promise.createError(error))
      return true;
    }

    val accessToken = urlDecoder.getParameter("at")!!
    val refreshToken = urlDecoder.getParameter("rt")!!
    val user = urlDecoder.getParameter("u")!!
    promise.setResult(Session(accessToken, refreshToken, user))
    Responses.send(html("You have been successfully authenticated"), context.channel(), request)
    return true
  }

  private fun failedAuth(context: ChannelHandlerContext, request: FullHttpRequest) {
    Responses.send(html("Failed to be authenticated. Internal server error, please see IDE logs"), context.channel(), request)
  }

  private fun html(message: String) = "<!doctype html><p>$message. <a href=\"javascript:window.open('','_self').close();\">Close window</a></p>"
}

data class Session(val accessToken: String, val refreshToken: String, val userId: String)

fun QueryStringDecoder.getParameter(name: String) = ContainerUtil.getLastItem<String, List<String>>(parameters().get(name))