package org.eclipse.flux.client

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.rabbitmq.client.*
import com.rabbitmq.client.AMQP.BasicProperties
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

class RabbitMqMessageConnector(executor: ExecutorService, configuration: RabbitMqFluxConfig) : BaseMessageConnector(executor) {
  private val connection: Connection
  private val commandProcessor = CommandProcessor<Map<String, Any>>()

  val channel: Channel
  val queue: String
  val exchangeCommands: String
  val exchangeEvents: String

  init {
    val connectionFactory = ConnectionFactory()
    configuration.applyTo(connectionFactory)
    connectionFactory.setSharedExecutor(executor)

    connection = connectionFactory.newConnection()
    LOG.info("Connected to rabbitMQ: " + configuration.uri)
    channel = connection.createChannel()
    queue = channel.queueDeclare().getQueue()
    setupConsumer()

    exchangeEvents = "t.${configuration.username}"
    channel.exchangeDeclare(exchangeEvents, "topic")
    // subscribe to all events
    channel.queueBind(queue, exchangeEvents, "#")

    exchangeCommands = "d.${configuration.username}"
    channel.exchangeDeclare(exchangeCommands, "direct")
    // bind by name to be able to accept direct replies (service response or topic response)
    channel.queueBind(queue, exchangeCommands, queue)
  }

  override fun doAddService(service: Service) {
    channel.queueBind(queue, exchangeCommands, service.name)
  }

  private fun setupConsumer() {
    channel.basicConsume(queue, object : DefaultConsumer(channel) {
      private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

      override fun handleDelivery(consumerTag: String?, envelope: Envelope, properties: BasicProperties, body: ByteArray) {
        try {
          [suppress("UNCHECKED_CAST")]
          val obj = gson.fromJson(body.inputStream.reader(), javaClass<Any>()) as Map<String, Any>
          [suppress("UNCHECKED_CAST")]
          val map = obj.get("data") as Map<String, Any>

          val correlationId = properties.getCorrelationId()
          if (correlationId == null) {
            // notification
            //  Tests whether an incoming message originated from the same MessageConnector that
            // todo is it really needed - maybe rabbitmq does it
            if (properties.getAppId() != queue) {
              handleEvent(envelope.getRoutingKey(), properties.getReplyTo(), properties.getCorrelationId(), map)
            }
          }
          else if (properties.getReplyTo() != null) {
            // request
            // we must not answer to yourself - we ask message broker to requeue request
            var rejected = properties.getAppId() == queue
            if (!rejected) {
              rejected = !reply(envelope.getRoutingKey(), properties.getType(), map, RabbitMqResult(properties.getReplyTo(), correlationId, envelope.getDeliveryTag()))
            }

            if (rejected) {
              // we must not answer to yourself - we ask message broker to requeue request
              channel.basicReject(envelope.getDeliveryTag(), true)
              return
            }
          }
          else if (properties.getType() == "eventResponse") {
            // response to broadcast request
            // todo real handler will not use it replyTo and correlationId, should be asserted somehow
            handleEvent(correlationId, "will be ignored in any case", "will be ignored in any case", map)
          }
          else {
            // response
            commandProcessor.getPromiseAndRemove(correlationId.toInt())?.setResult(map)
          }

          channel.basicAck(envelope.getDeliveryTag(), false)
        }
        catch (e: Throwable) {
          try {
            LOG.error(e.getMessage(), e)
          }
          finally {
            channel.basicReject(envelope.getDeliveryTag(), true)
          }
        }
      }
    })
  }

  inner class RabbitMqResult(private val replyTo: String, private val messageId: String, private val deliveryTag: Long) : Result {
    override fun write(writer: JsonWriter, byteOut: ByteArrayOutputStream) {
      channel.basicPublish(exchangeCommands, replyTo, BasicProperties.Builder().appId(queue).correlationId(messageId).build(), encode(writer, byteOut))
    }

    override fun reject(error: Throwable?) {
      try {
        if (error == null) {
          LOG.warn("$messageId rejected")
        }
        else {
          LOG.error("$messageId rejected, ${error.getMessage()}", error)
        }
      }
      finally {
        channel.basicReject(deliveryTag, true)
      }
    }
  }

  override fun notify(topic: Topic, writer: JsonWriter, byteOut: ByteArrayOutputStream) {
    val propertiesBuilder = BasicProperties.Builder().appId(queue)
    if (topic.responseName != null) {
      propertiesBuilder.correlationId(topic.responseName).replyTo(queue)
    }
    channel.basicPublish(exchangeEvents, topic.name, propertiesBuilder.build(), encode(writer, byteOut))
  }

  /**
   * In case of reply to event, we don't need to reject request, so, we don't use Result (RabbitMqResult) class
   */
  override fun replyToEvent(replyTo: String, correlationId: String, writer: JsonWriter, byteOut: ByteArrayOutputStream) {
    channel.basicPublish(exchangeCommands, replyTo, BasicProperties.Builder().appId(queue).correlationId(correlationId).type("eventResponse").build(), encode(writer, byteOut))
  }

  override fun request(method: Service.Method, writer: JsonWriter, byteOut: ByteArrayOutputStream): Promise<Map<String, Any>> {
    val requestId = commandProcessor.getNextId()
    val promise = AsyncPromise<Map<String, Any>>()
    commandProcessor.callbackMap.put(requestId, promise)
    try {
      val properties = BasicProperties.Builder().replyTo(queue).correlationId(requestId.toString()).type(method.name).build()
      channel.basicPublish(exchangeCommands, method.serviceName, properties, encode(writer, byteOut))
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

  fun encode(writer: JsonWriter, byteOut: ByteArrayOutputStream): ByteArray {
    writer.endObject()
    writer.close()
    return byteOut.toByteArray()
  }
}