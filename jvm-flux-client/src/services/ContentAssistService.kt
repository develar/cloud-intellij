package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service

trait ContentAssistService : Service {
  enum class Methods : Service.Method {
    override final val serviceName: String
      get() = "contentAssist"

    override final val name: String
      get() = name()

    contentAssist
  }

  override final val name: String
    get() = "contentAssist"

  protected fun get(projectName: String, resourcePath: String, offset: Int, prefix: String, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "get" -> {
        var project: String? = null
        var resource: String? = null
        var offset: Int = -1
        var prefix: String? = null
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            "resource" -> resource = reader.nextString()
            "offset" -> offset = reader.nextInt()
            "prefix" -> prefix = reader.nextString()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        get(project!!, resource!!, offset, prefix!!, result)
      }
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}

