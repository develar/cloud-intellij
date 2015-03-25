package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service

trait ResourceService : Service {
  enum class Methods : Service.Method {
    override final val serviceName: String
      get() = "resources"

    override final val name: String
      get() = name()

    getAll
    get
  }

  override val name: String
    get() = "resources"

  public fun get(projectName: String, resourcePath: String, hash: String, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "get" -> {
        var project: String? = null
        var resource: String? = null
        var hash: String? = null
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            "resource" -> resource = reader.nextString()
            "hash" -> hash = reader.nextString()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        get(project!!, resource!!, hash!!, result)
      }
      else -> {
        noMethod(methodName, result)
      }
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
        var resource: String? = null
        var offset: Int = -1
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            "resource" -> resource = reader.nextString()
            "offset" -> offset = reader.nextInt()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        renameInFile(project!!, resource!!, offset, result)
      }
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}

/**
 * Implements "Jump to declaration" navigation for a location.
 */
trait NavigationService : Service {
  override val name: String
    get() = "navigation"

  protected fun navigate(projectName: String, resourcePath: String, offset: Int, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "navigate" -> {
        var project: String? = null
        var resource: String? = null
        var offset: Int = -1
        val reader = JsonReader(request.inputStream.reader())
        reader.beginObject()
        while (reader.hasNext()) {
          when (reader.nextName()) {
            "project" -> project = reader.nextString()
            "resource" -> resource = reader.nextString()
            "offset" -> offset = reader.nextInt()
            else -> reader.skipValue()
          }
        }
        reader.endObject()

        navigate(project!!, resource!!, offset, result)
      }
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}

trait NavigationServiceBase : NavigationService {
  override final fun navigate(projectName: String, resourcePath: String, offset: Int, result: Result) {
    result.writeIf {
      if (computeNavigation(projectName, resourcePath, offset, it)) {
        it.name("project").value(projectName)
        it.name("resource").value(resourcePath)
        true
      }
      else {
        false
      }
    }
  }

  protected fun computeNavigation(projectName: String, resourcePath: String, offset: Int, writer: JsonWriter): Boolean
}