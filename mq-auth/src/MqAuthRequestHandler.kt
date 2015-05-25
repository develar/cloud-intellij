package org.jetbrains.flux.mqAuth

import io.netty.buffer.readChars
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpHeaders
import io.vertx.core.net.PemTrustOptions
import io.vertx.ext.apex.Router
import org.jetbrains.io.JsonReaderEx
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger(javaClass<AuthRequestHandler>())

public inline fun JsonReaderEx.map(f: JsonReaderEx.() -> Unit) {
  beginObject()
  while (hasNext()) {
    f()
  }
  endObject()
}

class AuthRequestHandler(vertx: Vertx, router: Router) {
  private val allow = Buffer.buffer("allow")
  private val allowMonitoring = Buffer.buffer("allow monitoring")
  private val deny = Buffer.buffer("deny")

  private val managementUser: String?

  init {
    managementUser = System.getenv("MANAGEMENT_USER")

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
    val httpClient = vertx.createHttpClient(httpClientOptions)

    router.route("/user").handler {
      val user = it.request().getParam("username")
      val accessToken = it.request().getParam("password")
      val response = it.response()
      if (user == managementUser) {
        response.end(allowMonitoring)
      }
      else if (user == null || accessToken == null) {
        response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end()
      }
      else {
        httpClient.get("/api/rest/users/me?fields=id,guest,banned")
          .putHeader(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
          .putHeader(HttpHeaders.ACCEPT, "application/json")
        .handler({
          val statusCode = it.statusCode()
          if (statusCode == HttpResponseStatus.OK.code()) {
            it.bodyHandler {
              var answer = deny
              try {
                JsonReaderEx(readChars(it.getByteBuf(), it.length())).map {
                  when (nextName()) {
                    "id" -> {
                      if (nextString() == user) {
                        answer = allow
                      }
                    }

                    "banned", "guest" -> {
                      if (nextBoolean()) {
                        answer = deny
                        return@map
                      }
                    }
                  }
                }
              }
              catch (e: Throwable) {
                LOG.error("Cannot check user $user", e)
              }
              finally {
                response.end(answer)
              }
            }
          }
          else {
            if (statusCode >= 400 && statusCode < 500 &&
              statusCode != HttpResponseStatus.UNAUTHORIZED.code() &&
              statusCode != HttpResponseStatus.FORBIDDEN.code()) {
              LOG.warn("Cannot check user $user, $statusCode ${it.statusMessage()}")
            }
            response.end(deny)
          }
        })
        .exceptionHandler {
          try {
            LOG.error("Cannot check user $user", it)
          }
          finally {
            response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end()
          }
        }
        .end()
      }
    }
    router.route("/resource").handler {
      val type = it.request().getParam("resource")!!
      assert(type == "exchange")
      val exchangeName = it.request().getParam("name")!!
      val userName = it.request().getParam("username")
      it.response().end(if ((exchangeName.length() - userName.length() == 2) &&
        (exchangeName[1] == '.' && (exchangeName[0] == 'd' || exchangeName[0] == 't')) &&
        exchangeName.regionMatches(2, userName, 0, userName.length(), false)) {
        allow
      }
      else {
        deny
      })
    }
  }
}