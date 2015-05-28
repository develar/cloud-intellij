package org.jetbrains.hub.oauth

import io.netty.buffer.readChars
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.net.PemTrustOptions
import org.jetbrains.flux.mqAuth.map
import org.jetbrains.io.JsonReaderEx
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("authServer")

fun createHubHttpClient(vertx: Vertx): HttpClient {
  val hubHost = System.getenv("HUB_HOST") ?: "hub"
  val hubPort = System.getenv("HUB_PORT")?.toInt() ?: 443
  val httpClientOptions = HttpClientOptions()
    .setTryUseCompression(true)
    .setDefaultHost(hubHost)
    .setDefaultPort(hubPort)
    .setSsl(hubPort == 443)

  val pemPath = System.getenv("CERT_PEM")
  if (pemPath != null) {
    httpClientOptions.setPemTrustOptions(PemTrustOptions().addCertPath(pemPath))
  }
  return vertx.createHttpClient(httpClientOptions)
}

fun getUserId(accessToken: String, response: HttpServerResponse, hubHttpClient: HttpClient, handler: (String?) -> Unit) {
  hubHttpClient.get("/api/rest/users/me?fields=id,guest,banned")
    .putHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
    .putHeader(HttpHeaders.ACCEPT, "application/json")
    .handler {
      val statusCode = it.statusCode()
      if (statusCode == HttpResponseStatus.OK.code()) {
        it.bodyHandler {
          handler(getUserIdIfNotBannedOrGuest(it))
        }
      }
      else {
        if (statusCode >= 400 && statusCode < 500 &&
          statusCode != HttpResponseStatus.UNAUTHORIZED.code() &&
          statusCode != HttpResponseStatus.FORBIDDEN.code()) {
          LOG.warn("Cannot check user, $statusCode ${it.statusMessage()}")
        }
        handler(null)
      }
    }
    .exceptionHandler {
      try {
        LOG.error("getMe failed", it)
      }
      finally {
        response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end()
      }
    }
    .end()
}

private fun getUserIdIfNotBannedOrGuest(data: Buffer): String? {
  var id: String? = null
  try {
    JsonReaderEx(readChars(data.getByteBuf(), data.length())).map {
      when (nextName()) {
        "id" -> id = nextString()

        "banned", "guest" -> {
          if (nextBoolean()) {
            return null
          }
        }
      }
    }
  }
  catch (e: Throwable) {
    LOG.error("Cannot check user", e)
  }
  return id
}