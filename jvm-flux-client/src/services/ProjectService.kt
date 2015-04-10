package org.eclipse.flux.client.services

import com.google.gson.stream.JsonReader
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.Service

trait ProjectService : Service {
  enum class Methods : Service.Method {
    override final val serviceName: String
      get() = "project"

    override final val name: String
      get() = name()

    getAll
  }

  override final val name: String
    get() = "project"

  public fun getAll(result: Result)

  override fun reply(methodName: String, request: ByteArray, result: Result) {
    when (methodName) {
      "getAll" -> getAll(result)
      else -> {
        noMethod(methodName, result)
      }
    }
  }
}