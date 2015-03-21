package org.eclipse.flux.client

/**
 * A Abstract MessageHandler that provides some help in implementing a typical
 * request -> response pattern. I.e. a handler that is accepting 'request' messages
 * and sends a single response back as a result.
 *
 * Subclasses must override either createResponse or fillResponse, whichever is
 * more convenient.
 */
public abstract class RequestResponseHandler(private val flux: MessageConnector, type: String) : BaseMessageHandler(type) {
  private val responseType = type.replaceAll("Request$", "Response")

  init {
    if (!responseType.endsWith("Response")) {
      LOG.error("responseType must ends with Response");
    }
  }

  override fun handle(type: String, message: Map<String, Any>) {
    try {
      flux.send(responseType, message.get(USERNAME) as String) {
        try {
          // copy as original flux client does, todo should we?
          copy(message, it)
          fillResponse(type, message, it)
        }
        catch (e: Throwable) {
          LOG.error(e.getMessage(), e)
          // todo reset writer
          errorResponse(message, e)
          return
        }
      }
    }
    catch (e: Throwable) {
      // This happens only if we had a problem sending the response
      LOG.error(e.getMessage(), e)
    }
  }

  protected fun copy(message: Map<String, Any>, writer: JsonWriter): Unit {
    for (name in message.keySet()) {
      val value = message.get(name)
      writer.name(name)
      if (value is String) {
        writer.value(value)
      }
      else if (value is Number) {
        writer.value(value)
      }
      else if (value is Boolean) {
        writer.value(value)
      }
      else if (value == null) {
        writer.nullValue()
      }
      else {
        LOG.error("Unsupported value type", value)
      }
    }
  }

  /**
   * Subclasses can implement this to fill in a response for the given request object.
   *
   *
   * If the method throws an exception it will be caught and turned into an error response
   * automatically.
   *
   *
   * If the method returns null, the request is silently ignored.
   *
   *
   * If the method returns a JSONObject this is sent as the response to the flux message bus.
   *
   *
   * For convenience a copy of the request object is passed in as as the 'res' parameter.
   * Implementers can add additional properties, modify the res object at will
   * or create a brand new object from scratch. However, when creating object from scratch
   * is is better to override 'createResponse' directly to avoid it creating a useless
   * copy of the req object.
   */
  protected open fun fillResponse(type: String, message: Map<String, Any>, result: JsonWriter) {
  }

  protected fun errorResponse(request: Map<String, Any>, error: Throwable) {
    val byteOut = ByteArrayOutputStream()
    val byteWriter = byteOut.writer()
    val writer = JsonWriter(byteWriter)
    writer.beginObject()
    copy(request, writer)
    writer.name("error").value(getMessage(error))
    writer.name("errorDetails").value(stackTrace(error))
//    flux.send(responseType, request, writer, byteOut)
  }
}