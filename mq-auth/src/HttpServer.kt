package org.jetbrains.httpServer

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.apex.Router
import org.jetbrains.flux.mqAuth.AuthRequestHandler

fun main(args: Array<String>) {
  val vertx = Vertx.vertx()

  val port = System.getenv("PORT")?.toInt() ?: 80
  val server = vertx.createHttpServer(HttpServerOptions().setCompressionSupported(true).setPort(port))
  val router = Router.router(vertx)

  AuthRequestHandler(vertx, router)

  server.requestHandler({ router.accept(it) }).listen {
    if (it.succeeded()) {
      System.out.println("Listening $port")
    }
    else {
      it.cause()?.printStackTrace()
    }
  }
}