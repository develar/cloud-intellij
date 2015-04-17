package org.intellij.flux

trait ProjectTopics {
    companion object {
        val connected = Topic("project.connected")
        val disconnected = Topic("project.disconnected")
    }
}

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