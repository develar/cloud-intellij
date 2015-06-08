package org.jetbrains.httpServer

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import org.jetbrains.flux.mqAuth.AuthRequestHandler
import org.jetbrains.hub.oauth.OAuthRequestHandler
import org.jetbrains.hub.oauth.createHubHttpClient

fun main(args: Array<String>) {
  System.out.println("Java: ${System.getProperty("java.runtime.version")} (${System.getProperty("java.vm.name")})")

  val vertx = Vertx.vertx()

  val port = System.getenv("PORT")?.toInt() ?: 80
  val server = vertx.createHttpServer(HttpServerOptions().setCompressionSupported(true).setPort(port))
  val router = Router.router(vertx)

  val httpClient = createHubHttpClient(vertx)
  AuthRequestHandler(httpClient, router)
  OAuthRequestHandler(httpClient, router)

  server.requestHandler({ router.accept(it) }).listen {
    if (it.succeeded()) {
      System.out.println("Listening $port")
    }
    else {
      it.cause()?.printStackTrace()
    }
  }
}