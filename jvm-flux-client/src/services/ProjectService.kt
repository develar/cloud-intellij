package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service

trait ProjectService : Service {
  enum class Methods : Service.Method {
    override final val serviceName: String
      get() = "projects"

    override final val name: String
      get() = name()

    getAll
    get
  }

  override final val name: String
    get() = "projects"

  public fun getAll(result: Result)

  public fun get(projectName: String, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "getAll" -> getAll(result)
      "get" -> {
        var project: String? = null
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        get(project!!, result)
      }
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}