package org.eclipse.flux.client

import com.google.gson.stream.JsonWriter
import org.jetbrains.util.concurrency.Promise
import java.io.ByteArrayOutputStream

trait Service {
  trait Method {
    val serviceName: String
    val name: String
  }

  val name: String

  fun reply(methodName: String, request: Map<String, Any>, result: Result)

  protected final fun noMethod(methodName: String, result: Result) {
    result.reject(NoSuchMethodException("No method $name.$methodName}"))
  }
}


trait Topic {
  val name: String

  // broadcast request (liveResourcesRequested -> n liveResources direct messages)
  val responseName: String?
    get() = null
}

trait Result {
  inline
  final fun write(writer: (JsonWriter) -> Unit) {
    writeIf({
      writer(it)
      true
    })
  }

  inline
  final fun writeIf(writer: (JsonWriter) -> Boolean) {
    val byteOut: ByteArrayOutputStream
    val jsonWriter: JsonWriter
    try {
      byteOut = ByteArrayOutputStream()
      jsonWriter = JsonWriter(byteOut.writer())
      jsonWriter.beginObject()
      if (!writer(jsonWriter)) {
        reject()
        return
      }
    }
    catch (e: Throwable) {
      reject(e)
      return
    }

    write(jsonWriter, byteOut)
  }

  fun write(writer: JsonWriter, byteOut: ByteArrayOutputStream)

  fun reject(error: Throwable? = null)
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

  inline
  public final fun notify(topic: Topic, writer: (JsonWriter) -> Unit) {
    val byteOut = ByteArrayOutputStream()
    val jsonWriter = JsonWriter(byteOut.writer())
    jsonWriter.beginObject()
    writer(jsonWriter)
    notify(topic, jsonWriter, byteOut)
  }

  inline
  public final fun request(method: Service.Method, writer: (JsonWriter) -> Unit): Promise<Map<String, Any>> {
    val byteOut = ByteArrayOutputStream()
    val jsonWriter = JsonWriter(byteOut.writer())
    jsonWriter.beginObject()
    writer(jsonWriter)
    return request(method, jsonWriter, byteOut)
  }

  public fun notify(topic: Topic, writer: JsonWriter, byteOut: ByteArrayOutputStream)

  public fun request(method: Service.Method, writer: JsonWriter, byteOut: ByteArrayOutputStream): Promise<Map<String, Any>>

  inline
  public final fun replyToEvent(replyTo: String, correlationId: String, writer: (JsonWriter) -> Unit) {
    val byteOut = ByteArrayOutputStream()
    val jsonWriter = JsonWriter(byteOut.writer())
    jsonWriter.beginObject()
    writer(jsonWriter)
    replyToEvent(replyTo, correlationId, jsonWriter, byteOut)
  }

  public fun replyToEvent(replyTo: String, correlationId: String, writer: JsonWriter, byteOut: ByteArrayOutputStream)
}