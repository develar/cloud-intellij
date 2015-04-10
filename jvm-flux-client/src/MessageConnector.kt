package org.eclipse.flux.client

import org.jetbrains.json.JsonWriterEx
import org.jetbrains.json.MapMemberWriter
import org.jetbrains.util.concurrency.Promise
import org.slf4j.LoggerFactory

val LOG = LoggerFactory.getLogger("flux-client")

trait Service {
  trait Method {
    val serviceName: String
    val name: String
  }

  val name: String

  fun reply(methodName: String, request: ByteArray, result: Result)

  protected final fun noMethod(methodName: String, result: Result) {
    result.reject(reason = "No method $name.$methodName}")
  }
}

class Topic(val name: String, responseExpected: Boolean = false) {
  // broadcast request (liveResourcesRequested -> n liveResources direct messages)
  val responseName = if (responseExpected) "$name.response" else null
}

public trait Result {
  inline final fun write(f: MapMemberWriter.() -> Unit) {
    val bytes: ByteArray
    try {
      val writer = JsonWriterEx()
      writer.beginObject()
      writer.f()
      writer.endObject()

      if (writer.out.isEmpty()) {
        reject("Empty response")
        return
      }
      else {
        bytes = writer.toByteArray()
      }
    }
    catch (e: Throwable) {
      reject(e)
      return
    }

    write(bytes)
  }

  fun write(byteArray: ByteArray)

  fun reject(error: Throwable)

  fun reject(reason: String)
}

/**
 * An instance of this interface represents a connection to flux message bus.
 *
 *
 * Clients may send messages by calling the 'send' method
 * and receive messages by adding message handlers.
 *
 *
 * A connection has the concept of 'channels' which corresponds to a 'user'
 * more or less. Messages in flux are generally intended for, or pertaining to
 * a specific user. By connecting to a user's channel the client becomes eligible
 * to send messages on behalf of this user and receive messages on behalf of the user.
 *
 *
 * Initially when a connection is created it is not connected to any user channel and
 * will only be eligible to send/receive messages that are sent explicitly to all users.
 *
 * If a client tries doing something it is not allowed to do, then an exception
 * may be thrown, or the operation can just silently fail.
 */
public trait MessageConnector {
  /**
   * Should connect to rpc queue only if is ready to handle messages
   */
  public fun addService(service: Service)

  inline
  public final fun on(topic: Topic, inlineOptions(InlineOption.ONLY_LOCAL_RETURN) handler: (message: Map<String, Any>) -> Unit) {
    replyOn(topic, { replyTo, correlationId, message ->
      handler(message)
    })
  }

  public fun replyOn(topic: Topic, handler: (replyTo: String, correlationId: String, message: Map<String, Any>) -> Unit)

  /**
   * Timeout (in milliseconds) for completing all the close-relate operations, use -1 for infinity
   */
  public fun close(timeout: Int = -1)

  public fun isConnected(): Boolean

  public inline final fun notify(topic: Topic, f: MapMemberWriter.() -> Unit) {
    val writer = JsonWriterEx()
    writer.map {
      f()
    }
    notify(topic, writer.toByteArray())
  }

  public inline final fun request(method: Service.Method, f: MapMemberWriter.() -> Unit): Promise<Map<String, Any>> {
    val writer = JsonWriterEx()
    writer.map {
      f()
    }
    return request(method, writer.toByteArray())
  }

  public fun notify(topic: Topic, byteArray: ByteArray)

  public fun request(method: Service.Method, byteArray: ByteArray): Promise<Map<String, Any>>

  public inline final fun replyToEvent(replyTo: String, correlationId: String, f: MapMemberWriter.() -> Unit) {
    val writer = JsonWriterEx()
    writer.map {
      f()
    }
    replyToEvent(replyTo, correlationId, writer.toByteArray())
  }

  public fun replyToEvent(replyTo: String, correlationId: String, byteArray: ByteArray)
}