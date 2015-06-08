package org.jetbrains.flux.mqAuth

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.ext.web.Router
import org.jetbrains.hub.oauth.getUserId
import org.jetbrains.io.JsonReaderEx

public inline fun JsonReaderEx.map(f: JsonReaderEx.() -> Unit) {
  beginObject()
  while (hasNext()) {
    f()
  }
  endObject()
}

class AuthRequestHandler(hubHttpClient: HttpClient, router: Router) {
  private val allow = Buffer.buffer("allow")
  private val allowMonitoring = Buffer.buffer("allow monitoring")
  private val deny = Buffer.buffer("deny")

  private val managementUser: String?

  init {
    managementUser = System.getenv("MANAGEMENT_USER")

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
        getUserId(accessToken, response, hubHttpClient) {
          response.end(if (user == it) allow else deny)
        }
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