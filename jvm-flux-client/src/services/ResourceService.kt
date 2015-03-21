package org.eclipse.flux.client.services

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

  protected fun get(projectName: String, resourcePath: String, hash: String, result: Result)

  override fun reply(methodName: String, request: Map<String, Any>, result: Result) {
    when (methodName) {
      "get" -> get(request.get("project") as String, request.get("resource") as String, request.get("hash") as String, result)
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

  protected fun renameInFile(request: Map<String, Any>, result: Result)

  override fun reply(methodName: String, request: Map<String, Any>, result: Result) {
    when (methodName) {
      "renameInFile" -> renameInFile(request, result)
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

  protected fun navigate(request: Map<String, Any>, result: Result)

  override fun reply(methodName: String, request: Map<String, Any>, result: Result) {
    when (methodName) {
      "navigate" -> navigate(request, result)
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}