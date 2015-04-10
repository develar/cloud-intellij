package org.eclipse.flux.client

import com.google.gson.GsonBuilder
import com.rabbitmq.client.*
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.impl.DefaultExceptionHandler
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.net.URI
import java.util.concurrent.ExecutorService

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

/**
 * rpcQueueName — see Implementation.md
 */
public class RabbitMqMessageConnector(username: String, executor: ExecutorService, rpcQueueName: String, uri: String = mqUri) : BaseMessageConnector() {
  private val connection: Connection
  private val commandProcessor = CommandProcessor<Map<String, Any>>()

  val channel: Channel
  val queue: String
  val rpcQueue: String
  val exchangeCommands: String
  val exchangeEvents: String

  init {
    val connectionFactory = ConnectionFactory()
    connectionFactory.setUri(uri)
    connectionFactory.setUsername(username)
    connectionFactory.setRequestedHeartbeat(60)
    connectionFactory.setAutomaticRecoveryEnabled(true)

    connectionFactory.setSharedExecutor(executor)
    connectionFactory.setExceptionHandler(object : DefaultExceptionHandler() {
      override fun handleUnexpectedConnectionDriverException(conn: Connection, exception: Throwable) {
        LOG.error(exception.getMessage(), exception)
      }
    })

    connection = connectionFactory.newConnection()
    LOG.info("Connected to rabbitMQ: " + uri)
    channel = connection.createChannel()
    channel.addShutdownListener(object: ShutdownListener {
      override fun shutdownCompleted(cause: ShutdownSignalException) {
        LOG.info(cause.getMessage())
      }
    })

    exchangeEvents = "t.$username"
    channel.exchangeDeclare(exchangeEvents, "topic")

    exchangeCommands = "d.$username"
    channel.exchangeDeclare(exchangeCommands, "direct")

    queue = channel.queueDeclare().getQueue()
    channel.basicConsume(queue, true, EventOrResponseConsumer())
    // subscribe to all events
    channel.queueBind(queue, exchangeEvents, "#")
    // bind by name to be able to accept direct replies (service response or topic response)
    channel.queueBind(queue, exchangeCommands, queue)

    rpcQueue = channel.queueDeclare(rpcQueueName, false, false, true, mapOf("x-dead-letter-exchange" to exchangeCommands)).getQueue()
    channel.basicConsume(rpcQueue, false, RequestConsumer())
  }

  override fun doAddService(service: Service) {
    channel.queueBind(rpcQueue, exchangeCommands, service.name)
  }

  inner class EventOrResponseConsumer : DefaultConsumer(channel) {
    private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

    override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: ByteArray) {
      try {
        val correlationId = properties.getCorrelationId()
        val replyTo = properties.getReplyTo()
        if (properties.getType() == "eventResponse") {
          // response to broadcast request
          // todo real handler will not use it replyTo and correlationId, should be asserted somehow
          [suppress("UNCHECKED_CAST")]
          val data = gson.fromJson(body.inputStream.reader(), javaClass<Any>()) as Map<String, Any>
          handleEvent(correlationId, "will be ignored in any case", "will be ignored in any case", data)
        }
        else if (correlationId == null || replyTo != null) {
          // event
          if (properties.getAppId() != queue) {
            [suppress("UNCHECKED_CAST")]
            val data = gson.fromJson(body.inputStream.reader(), javaClass<Any>()) as Map<String, Any>
            handleEvent(envelope.getRoutingKey(), replyTo ?: "not a broadcast event", correlationId ?: "not a broadcast event", data)
          }
        }
        else {
          // response
          [suppress("UNCHECKED_CAST")]
          val data = gson.fromJson(body.inputStream.reader(), javaClass<Any>()) as Map<String, Any>
          val promise = commandProcessor.getPromiseAndRemove(correlationId.toInt())
          try {
            promise?.setResult(data)
          }
          catch (e: Throwable) {
            LOG.error(e.getMessage(), e)
          }
        }
      }
      catch (e: Throwable) {
        // RabbitMQ also handle consumer exceptions and we can provide our own implementation of com.rabbitmq.client.ExceptionHandler,
        // but it is more correct in our case (we delegate consuming) to handle it here
        LOG.error(e.getMessage(), e)
      }
    }
  }

  inner class RequestConsumer : DefaultConsumer(channel) {
    override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: ByteArray) {
      try {
        if (properties.getHeaders().get("x-death") != null) {
          throw UnsupportedOperationException();
        }

        // we must not answer to yourself - we ask message broker to requeue request
        if (properties.getAppId() == queue) {
          // we must not answer to yourself - we ask message broker to requeue request
          channel.basicReject(envelope.getDeliveryTag(), true)
          return
        }

        reply(envelope.getRoutingKey(), properties.getType()!!, body, RabbitMqResult(properties.getReplyTo()!!, properties.getCorrelationId(), envelope.getDeliveryTag()))
      }
      catch (e: Throwable) {
        // RabbitMQ also handle consumer exceptions and we can provide our own implementation of com.rabbitmq.client.ExceptionHandler,
        // but it is more correct in our case (we delegate consuming) to handle it here
        try {
          LOG.error(e.getMessage(), e)
        }
        finally {
          channel.basicReject(envelope.getDeliveryTag(), true)
        }
      }
    }
  }

  inner class RabbitMqResult(private val replyTo: String, private val messageId: String, private val deliveryTag: Long) : Result {
    override fun write(byteArray: ByteArray) {
      channel.basicAck(deliveryTag, false)
      channel.basicPublish(exchangeCommands, replyTo, BasicProperties.Builder().appId(queue).correlationId(messageId).build(), byteArray)
    }

    override fun reject(reason: String) {
      try {
        LOG.warn("$messageId rejected, reason: $reason")
      }
      finally {
        channel.basicReject(deliveryTag, true)
      }
    }

    override fun reject(error: Throwable) {
      try {
        LOG.error("$messageId rejected, ${error.getMessage()}", error)
      }
      finally {
        channel.basicReject(deliveryTag, true)
      }
    }
  }

  override fun notify(topic: Topic, byteArray: ByteArray) {
    val propertiesBuilder = BasicProperties.Builder().appId(queue)
    if (topic.responseName != null) {
      propertiesBuilder
              .correlationId(topic.responseName)
              .replyTo(queue)
    }
    channel.basicPublish(exchangeEvents, topic.name, propertiesBuilder.build(), byteArray)
  }

  /**
   * In case of reply to event, we don't need to reject request, so, we don't use Result (RabbitMqResult) class
   */
  override fun replyToEvent(replyTo: String, correlationId: String, byteArray: ByteArray) {
    channel.basicPublish(exchangeCommands, replyTo, BasicProperties.Builder().appId(queue).correlationId(correlationId).type("eventResponse").build(), byteArray)
  }

  override fun request(method: Service.Method, byteArray: ByteArray): Promise<Map<String, Any>> {
    val requestId = commandProcessor.getNextId()
    val promise = AsyncPromise<Map<String, Any>>()
    commandProcessor.callbackMap.put(requestId, promise)
    try {
      val properties = BasicProperties.Builder().replyTo(queue).correlationId(requestId.toString()).type(method.name).build()
      channel.basicPublish(exchangeCommands, method.serviceName, properties, byteArray)
    }
    catch (e: Throwable) {
      try {
        LOG.error("Failed to send", e)
      }
      finally {
        commandProcessor.failedToSend(requestId)
      }
    }
    return promise
  }

  override fun close(timeout: Int) {
    try {
      // we don't unbind the queue from the topic exchange - our queue is exclusive and auto-delete.
      connection.close(timeout)
    }
    catch (e: Throwable) {
      LOG.error(e.getMessage(), e)
    }
  }

  override fun isConnected() = connection.isOpen()
}