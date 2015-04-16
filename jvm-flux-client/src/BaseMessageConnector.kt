package org.eclipse.flux.client

import org.jetbrains.util.concurrency.AsyncPromise
import org.jetbrains.util.concurrency.Promise
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

public abstract class BaseMessageConnector() : MessageConnector {
  private val services: ConcurrentHashMap<String, Service> = ConcurrentHashMap()
  private val eventHandlers = ConcurrentHashMap<String, MutableCollection<(replyTo: String, correlationId: String, message: ByteArray) -> Unit>>()

  protected fun reply(serviceName: String, methodName: String, message: ByteArray, result: Result) {
    val service = services.get(serviceName)
    if (service == null) {
      result.reject(reason = "No service $serviceName")
      return
    }

    try {
      service.reply(methodName, message, result)
    }
    catch (e: Throwable) {
      result.reject(e)
    }
  }

  protected fun handleEvent(topic: String, replyTo: String, correlationId: String, message: ByteArray) {
    for (handler in eventHandlers.get(topic)) {
      try {
        handler(replyTo, correlationId, message)
      }
      catch (e: Throwable) {
        LOG.error(e.getMessage(), e)
      }
    }
  }

  override fun replyOn(topic: Topic, handler: (replyTo: String, correlationId: String, message: ByteArray) -> Unit) {
    var list = eventHandlers.get(topic)
    if (list == null) {
      list = ConcurrentLinkedDeque<(replyTo: String, correlationId: String, message: ByteArray) -> Unit>()
      val existingList = eventHandlers.putIfAbsent(topic.name, list)
      if (existingList != null) {
        list = existingList
      }
    }
    list.add(handler)
  }

  override final fun addService(service: Service) {
    val existing = services.putIfAbsent(service.name, service)
    if (existing != null) {
      throw IllegalStateException("Service ${service.name} is already registered");
    }

    var success = false
    try {
      doAddService(service)
      success = true
    }
    finally {
      if (!success) {
        services.remove(service.name, service)
      }
    }
  }

  protected abstract fun doAddService(service: Service)
}

class CommandProcessor<T> {
  private val requestIdCounter = AtomicInteger()
  // todo use efficient intellij ContainerUtil.createConcurrentIntObjectMap
  val callbackMap = ConcurrentHashMap<Int, AsyncPromise<T>>()

  companion object {
    val ID_MAX_VALUE = java.lang.Integer.MAX_VALUE
  }

  fun getNextId(): Int {
    val id = requestIdCounter.incrementAndGet()
    if (id >= ID_MAX_VALUE) {
      requestIdCounter.compareAndSet(id, -1)
      return requestIdCounter.incrementAndGet()
    }
    return id
  }

  fun failedToSend(id: Int) {
    callbackMap.remove(id)?.setError(Promise.createError("Failed to send"))
  }

  fun getPromiseAndRemove(id: Int): AsyncPromise<T>? {
    val callback = callbackMap.remove(id)
    if (callback == null) {
      LOG.warn("Cannot find callback with id $id")
    }
    return callback
  }
}