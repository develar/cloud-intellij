package org.jetbrains.hub.oauth

import io.netty.buffer.readChars
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringEncoder
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.apex.Router
import org.jetbrains.flux.mqAuth.map
import org.jetbrains.io.JsonReaderEx
import org.slf4j.LoggerFactory
import java.util.Base64

private val LOG = LoggerFactory.getLogger(javaClass<OAuthRequestHandler>())

fun getRequiredEnv(name: String) = System.getenv(name) ?: throw RuntimeException("Environment variable $name must be specified")

class OAuthRequestHandler(hubHttpClient: HttpClient, router: Router) {
  init {
    val clientId = getRequiredEnv("CLIENT_ID")
    val clientSecret = getRequiredEnv("CLIENT_SECRET")
    val authorization = "Basic ${Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())}"

//    router.route("/oauth").handler(CorsHandler.create(System.getenv("CORS_ORIGIN") ?: "*").allowedMethod(HttpMethod.GET))
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

        val buffer = Buffer.buffer("code=$code&grant_type=authorization_code&redirect_uri=https%3A%2F%2Fflux.dev%2Foauth")
        hubHttpClient.post("/api/rest/oauth2/token")
          .putHeader(HttpHeaders.CONTENT_LENGTH, buffer.length().toString())
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
          .putHeader(HttpHeaders.AUTHORIZATION, authorization)
          .putHeader(HttpHeaders.ACCEPT, "application/json")
          .handler {
            val statusCode = it.statusCode()
            it.bodyHandler {
              val urlEncoder = QueryStringEncoder("http://127.0.0.1:$port/67822818-87E4-4FF9-81C5-75433D57E7B3")
              urlEncoder.addParam("r", requestId)

              val data = readChars(it.getByteBuf(), it.length())
              if (statusCode == HttpResponseStatus.OK.code()) {
                JsonReaderEx(data).map {
                  when (nextName()) {
                    "access_token" -> urlEncoder.addParam("at", nextString())
                    "refresh_token" -> urlEncoder.addParam("rt", nextString())
                    else -> skipValue()
                  }
                }
              }
              else {
                // send error as is
                urlEncoder.addParam("e", data.toString())
              }

              response
                .setStatusCode(HttpResponseStatus.FOUND.code())
                .putHeader(HttpHeaders.LOCATION, urlEncoder.toString())
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