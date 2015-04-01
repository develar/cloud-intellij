package org.eclipse.flux.client

import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonWriter
import com.rabbitmq.client.*
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.impl.DefaultExceptionHandler
import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService

class RabbitMqMessageConnector(executor: ExecutorService, configuration: RabbitMqFluxConfig) : BaseMessageConnector() {
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
    connectionFactory.setExceptionHandler(object : DefaultExceptionHandler() {
      override fun handleUnexpectedConnectionDriverException(conn: Connection, exception: Throwable) {
        LOG.error(exception.getMessage(), exception)
      }
    })

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
    // we confirm delivery before user handlers logic, except request.
    // if user handler cannot handle event/response - it is not our issue, the event/response was delivered correctly
    // we cannot use auto ack, because we have the one queue for all types of messages.
    channel.basicConsume(queue, object : DefaultConsumer(channel) {
      private val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

      override fun handleShutdownSignal(consumerTag: String, signal: ShutdownSignalException) {
        LOG.info(signal.getMessage())
      }

      override fun handleDelivery(consumerTag: String, envelope: Envelope, properties: BasicProperties, body: ByteArray) {
        try {
          val correlationId = properties.getCorrelationId()
          val type = properties.getType()
          val replyTo = properties.getReplyTo()
          if (type != null) {
            if (type == "eventResponse") {
              channel.basicAck(envelope.getDeliveryTag(), false)

              // response to broadcast request
              // todo real handler will not use it replyTo and correlationId, should be asserted somehow
              [suppress("UNCHECKED_CAST")]
              val data = gson.fromJson(body.inputStream.reader(), javaClass<Any>()) as Map<String, Any>
              handleEvent(correlationId, "will be ignored in any case", "will be ignored in any case", data)
            }
            else {
              // request
              // we must not answer to yourself - we ask message broker to requeue request
              if (properties.getAppId() == queue) {
                // we must not answer to yourself - we ask message broker to requeue request
                channel.basicReject(envelope.getDeliveryTag(), true)
              }
              else {
                reply(envelope.getRoutingKey(), type, body, RabbitMqResult(replyTo!!, correlationId, envelope.getDeliveryTag()))
              }
            }
          }
          else {
            channel.basicAck(envelope.getDeliveryTag(), false)
            if (correlationId == null || replyTo != null) {
              // event
              //  Tests whether an incoming message originated from the same MessageConnector that
              // todo is it really needed - maybe rabbitmq does it
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
    })
  }

  inner class RabbitMqResult(private val replyTo: String, private val messageId: String, private val deliveryTag: Long) : Result {
    override fun write(writer: JsonWriter, byteOut: ByteArrayOutputStream) {
      channel.basicAck(deliveryTag, false)
      channel.basicPublish(exchangeCommands, replyTo, BasicProperties.Builder().appId(queue).correlationId(messageId).build(), encode(writer, byteOut))
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

  override fun notify(topic: Topic, writer: JsonWriter, byteOut: ByteArrayOutputStream) {
    val propertiesBuilder = BasicProperties.Builder().appId(queue)
    if (topic.responseName != null) {
      propertiesBuilder
              .correlationId(topic.responseName)
              .replyTo(queue)
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