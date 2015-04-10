package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service
import org.jetbrains.json.ArrayMemberWriter
import org.jetbrains.json.MapMemberWriter
import org.jetbrains.json.jsonReader
import org.jetbrains.json.map

trait EditorService : Service {
  override val name: String
    get() = "editor"

  public fun navigate(projectName: String, resourcePath: String, offset: Int, result: Result)

  public fun computeProblems(projectName: String, resourcePath: String, result: Result)

  public fun computeContentAssist(projectName: String, resourcePath: String, offset: Int, prefix: String, result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "navigate" -> {
        var project: String? = null
        var resource: String? = null
        var offset: Int = -1
        request.jsonReader().map {
          when (nextName()) {
            "project" -> project = nextString()
            "resource" -> resource = nextString()
            "offset" -> offset = nextInt()
            else -> skipValue()
          }
        }

        navigate(project!!, resource!!, offset, result)
      }

      "problems" -> {
        var project: String? = null
        var path: String? = null
        request.jsonReader().map {
          when (nextName()) {
            "project" -> project = nextString()
            "path" -> path = nextString()
            else -> skipValue()
          }
        }

        computeProblems(project!!, path!!, result)
      }

      "contentAssist" -> {
        var project: String? = null
        var path: String? = null
        var offset: Int = -1
        var prefix: String? = null
        request.jsonReader().map {
          when (nextName()) {
            "project" -> project = nextString()
            "path" -> path = nextString()
            "offset" -> offset = nextInt()
            "prefix" -> prefix = nextString()
            else -> skipValue()
          }
        }

        computeContentAssist(project!!, path!!, offset, prefix!!, result)
      }

      else -> {
        noMethod(methodName, result)
      }
    }
  }
}

trait EditorServiceBase : EditorService {
  override final fun navigate(projectName: String, resourcePath: String, offset: Int, result: Result) {
    result.write {
      if (!computeNavigation(projectName, resourcePath, offset)) {
        "error"("not found")
      }
    }
  }

  override final fun computeContentAssist(projectName: String, resourcePath: String, offset: Int, prefix: String, result: Result) {
    result.write {
      array("list") {
        computeContentAssist(projectName, resourcePath, offset, prefix)
      }
    }
  }

  protected fun ArrayMemberWriter.computeContentAssist(projectName: String, resourcePath: String, offset: Int, prefix: String): Boolean

  protected fun MapMemberWriter.computeNavigation(projectName: String, resourcePath: String, offset: Int): Boolean
}