package org.eclipse.flux.client.services

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

  override fun reply(methodName: String, request: Map<String, Any>, result: Result) {
    when (methodName) {
      "get" -> get(request.get("project") as String, request.get("resource") as String, request.get("offset") as Int, request.get("prefix") as String, result)
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}

