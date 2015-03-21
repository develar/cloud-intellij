package org.eclipse.flux.client

import com.rabbitmq.client.ConnectionFactory
import java.net.URI
import java.util.concurrent.ExecutorService

public fun superConfig(): FluxConfig = RabbitMqFluxConfig(SUPER_USER, mqUri)

fun isProperty(name: String, default: Boolean = false): Boolean {
  val value = System.getProperty(name)
  return if (value == null) default else (value.isEmpty() || value.toBoolean())
}

val mqUri: String
  get() {
    var host = System.getenv("MQ_HOST")
    if (host == null && isProperty("docker.host.as.mq.host")) {
      host = URI(System.getenv("DOCKER_HOST")).getHost()!!
    }
    return "amqp://${host ?: "mq"}"
  }

class RabbitMqFluxConfig(username: String = SUPER_USER, val uri: String = mqUri) : BaseFluxConfig(username) {
  override fun connect(executor: ExecutorService) = RabbitMqMessageConnector(executor, this)

  fun applyTo(factory: ConnectionFactory) {
    factory.setUri(uri)
    factory.setUsername(username)
    factory.setRequestedHeartbeat(60)
    factory.setAutomaticRecoveryEnabled(true)
  }
}