package org.jetbrains.httpServer

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.ext.apex.Router
import org.jetbrains.flux.mqAuth.AuthRequestHandler
import org.jetbrains.hub.oauth.OAuthRequestHandler

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

private fun createHubHttpClient(vertx: Vertx): HttpClient {
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