package org.jetbrains.hub.oauth

import io.netty.buffer.readChars
import io.netty.handler.codec.http.HttpConstants
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.apex.Router
import org.jetbrains.flux.mqAuth.map
import org.jetbrains.io.JsonReaderEx
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.util.Base64

private val LOG = LoggerFactory.getLogger(javaClass<OAuthRequestHandler>())

fun getRequiredEnv(name: String) = System.getenv(name) ?: throw RuntimeException("Environment variable $name must be specified")

fun encodeQueryComponent(s: String) = encodeFormComponent(s).replace("+", "%20")

fun encodeFormComponent(s: String) = URLEncoder.encode(s, HttpConstants.DEFAULT_CHARSET.name())

class OAuthRequestHandler(hubHttpClient: HttpClient, router: Router) {
  init {
    val clientId = getRequiredEnv("CLIENT_ID")
    val clientSecret = getRequiredEnv("CLIENT_SECRET")
    val authorization = "Basic ${Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())}"

    // router.route("/oauth").handler(CorsHandler.create(System.getenv("CORS_ORIGIN") ?: "*").allowedMethod(HttpMethod.GET))
    router.route("/oauth").handler {
      val response = it.response()
      val code = it.request().getParam("code")
      val state = it.request().getParam("state")
      if (code == null || state == null) {
        response.setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end()
      }
      else {
        val index = state.indexOf(':')
        val port = state.substring(0, index)
        val requestId = state.substring(index + 1)

        fun buildRequestUrl(): String {
          val headers = it.request().headers()
          val scheme = headers.get("X-Forwarded-Proto") ?: "https"
          val host = headers.get("X-Forwarded-Host") ?: headers.get("Host")!!
          return "code=${encodeQueryComponent(code)}&grant_type=authorization_code&redirect_uri=$scheme%3A%2F%2F${encodeQueryComponent(host)}/oauth"
        }

        val buffer = Buffer.buffer(buildRequestUrl())
        hubHttpClient.post("/api/rest/oauth2/token")
          .putHeader(HttpHeaders.CONTENT_LENGTH, buffer.length().toString())
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
          .putHeader(HttpHeaders.AUTHORIZATION, authorization)
          .putHeader(HttpHeaders.ACCEPT, "application/json")
          .handler {
            val statusCode = it.statusCode()
            it.bodyHandler {
              val redirectUrlBuilder = StringBuilder("http://127.0.0.1:").append(port).append("/67822818-87E4-4FF9-81C5-75433D57E7B3")
              redirectUrlBuilder.append("?r=").append(requestId)

              val data = readChars(it.getByteBuf(), it.length())
              if (statusCode == HttpResponseStatus.OK.code()) {
                JsonReaderEx(data).map {
                  when (nextName()) {
                    "access_token" -> redirectUrlBuilder.append("&at=").append(encodeFormComponent(nextString()))
                    "refresh_token" -> redirectUrlBuilder.append("&rt=").append(encodeFormComponent(nextString()))
                    else -> skipValue()
                  }
                }
              }
              else {
                // send error as is
                redirectUrlBuilder.append("&e=").append(encodeFormComponent(data.toString()))
              }

              response
                .setStatusCode(HttpResponseStatus.FOUND.code())
                .putHeader(HttpHeaders.LOCATION, redirectUrlBuilder.toString())
                .end()
            }
          }
          .exceptionHandler {
            try {
              LOG.error("Cannot get access token", it)
            }
            finally {
              response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end()
            }
          }
          .end(buffer)
      }
    }
  }
}