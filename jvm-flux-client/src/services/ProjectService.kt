package org.eclipse.flux.client.services

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

  protected fun getAll(request: Map<String, Any>, result: Result)

  protected fun get(request: Map<String, Any>, result: Result)

  override fun reply(methodName: String, request: Map<String, Any>, result: Result) {
    when (methodName) {
      "getAll" -> getAll(request, result)
      "get" -> get(request, result)
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}