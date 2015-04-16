package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service
import org.jetbrains.json.MessageWriter
import org.jetbrains.json.jsonReader
import org.jetbrains.json.map
import org.jetbrains.json.nextNullableString

trait ResourceService : Service {
  enum class Methods : Service.Method {
    override final val serviceName: String
      get() = "resource"

    override final val name: String
      get() = name()
  }

  override val name: String
    get() = "resource"

  public fun get(projectName: String, path: String?, requestorHash: String?, includeContents: Boolean, result: Result)

  public fun contentTypes(result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "get" -> {
        var project: String? = null
        var path: String? = null
        var hash: String? = null
        var contents = true
        request.jsonReader().map {
          when (nextName()) {
            "project" -> project = nextString()
            "path" -> path = nextNullableString()
            "hash" -> hash = nextNullableString()
            "contents" -> contents = nextBoolean()
            else -> skipValue()
          }
        }

        get(project!!, path, hash, contents, result)
      }
      "contentTypes" -> {
        contentTypes(result)
      }
      else -> noMethod(methodName, result)
    }
  }
}

/**
 * Responds to requests to rename a member and update all local references.
 */
trait RenameService : Service {
  override val name: String
    get() = "rename"

  protected fun renameInFile(projectName: String, resourcePath: String, offset: Int, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "renameInFile" -> {
        var project: String? = null
        var path: String? = null
        var offset: Int = -1
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            "path" -> path = reader.nextString()
            "offset" -> offset = reader.nextInt()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        renameInFile(project!!, path!!, offset, result)
      }
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}