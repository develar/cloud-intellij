/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.util.concurrency

import org.slf4j.LoggerFactory
import java.util.ArrayList

public class AsyncPromise<T> : Promise<T> {
  volatile private var done: ((T) -> Unit)? = null
  volatile private var rejected: ((Throwable) -> Unit)? = null

  volatile override public var state = Promise.State.PENDING
    private set

  // result object or error message
  volatile private var result: Any? = null

  companion object {
    private val LOG = LoggerFactory.getLogger(javaClass<AsyncPromise<Any>>())

    public val OBSOLETE_ERROR: RuntimeException = Promise.createError("Obsolete")

    private fun <T> setHandler(oldConsumer: ((T) -> Unit)?, newConsumer: (T) -> Unit): (T) -> Unit {
      if (oldConsumer == null) {
        return newConsumer
      }
      else if (oldConsumer is CompoundConsumer<*>) {
        (oldConsumer as CompoundConsumer<T>).add(newConsumer)
        return oldConsumer
      }
      else {
        return CompoundConsumer(oldConsumer, newConsumer)
      }
    }
  }

  override fun done(done: (T) -> Unit): Promise<T> {
    if (isObsolete(done)) {
      return this
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        done(result as T)
        return this
      }
      Promise.State.REJECTED -> return this
    }

    this.done = setHandler(this.done, done)
    return this
  }

  override fun rejected(rejected: (Throwable) -> Unit): Promise<T> {
    if (isObsolete(rejected)) {
      return this
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> return this
      Promise.State.REJECTED -> {
        rejected(result as Throwable)
        return this
      }
    }

    this.rejected = setHandler(this.rejected, rejected)
    return this
  }

  public fun get(): T {
    @suppress("UNCHECKED_CAST")
    return when (state) {
      Promise.State.FULFILLED -> result as T
      else -> null
    }
  }

  SuppressWarnings("SynchronizeOnThis")
  private class CompoundConsumer<T>(c1: (T) -> Unit, c2: (T) -> Unit) : (T) -> Unit {
    private var consumers: MutableList<(T) -> Unit>? = ArrayList()

    init {
      synchronized (this) {
        consumers!!.add(c1)
        consumers!!.add(c2)
      }
    }

    override fun invoke(p1: T) {
      var list: List<(T) -> Unit>? = null
      synchronized (this) {
        list = consumers!!
        consumers = null
      }

      if (list != null) {
        for (consumer in list!!) {
          if (!isObsolete(consumer)) {
            consumer(p1)
          }
        }
      }
    }

    public fun add(consumer: (T) -> Unit) {
      synchronized (this) {
        if (consumers != null) {
          consumers!!.add(consumer)
        }
      }
    }
  }

  override fun <SUB_RESULT> then(done: (T) -> SUB_RESULT): Promise<SUB_RESULT> {
    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        return DonePromise(done(result as T))
      }
      Promise.State.REJECTED -> return RejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    addHandlers({
                  try {
                    if (done is Obsolescent && done.isObsolete()) {
                      promise.setError(OBSOLETE_ERROR)
                    }
                    else {
                      promise.setResult(done(it))
                    }
                  }
                  catch (e: Throwable) {
                    promise.setError(e)
                  }
                },
                {
                  promise.setError(it)
                })
    return promise
  }

  override fun notify(child: AsyncPromise<T>) {
    if (child == this) {
      throw IllegalStateException("Child must no be equals to this")
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        child.setResult(result as T)
        return
      }
      Promise.State.REJECTED -> {
        child.setError(result as Throwable)
        return
      }
    }

    addHandlers({
                  try {
                    child.setResult(it)
                  }
                  catch (e: Throwable) {
                    child.setError(e)
                  }
                },
                {
                  child.setError(it)
                }
    )
  }

  override fun <SUB_RESULT> then(done: AsyncFunction<T, SUB_RESULT>): Promise<SUB_RESULT> {
    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        return done.`fun`(result as T)
      }
      Promise.State.REJECTED -> return Promise.reject<SUB_RESULT>(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    val rejectedHandler = fun (it: Throwable): Unit {
      promise.setError(it)
    }
    addHandlers({
                  try {
                    done.`fun`(it).done({
                                          try {
                                            promise.setResult(it)
                                          }
                                          catch (e: Throwable) {
                                            promise.setError(e)
                                          }
                                        })
                            .rejected(rejectedHandler)
                  }
                  catch (e: Throwable) {
                    promise.setError(e)
                  }
                },
                rejectedHandler)
    return promise
  }

  override fun processed(fulfilled: AsyncPromise<T>): Promise<T> {
    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        fulfilled.setResult(result as T)
        return this
      }
      Promise.State.REJECTED -> {
        fulfilled.setError(result as Throwable)
        return this
      }
    }

    addHandlers({
                  try {
                    fulfilled.setResult(it)
                  }
                  catch (e: Throwable) {
                    fulfilled.setError(e)
                  }
                }
                , {
                  fulfilled.setError(it)
                })
    return this
  }

  private fun addHandlers(done: (T) -> Unit, rejected: (Throwable) -> Unit) {
    this.done = setHandler(this.done, done)
    this.rejected = setHandler(this.rejected, rejected)
  }

  public fun setResult(result: T) {
    if (state != Promise.State.PENDING) {
      return
    }

    this.result = result
    state = Promise.State.FULFILLED

    val done = this.done
    clearHandlers()
    if (done != null && !isObsolete(done)) {
      done(result)
    }
  }

  public fun setError(error: Throwable): Boolean {
    if (state != Promise.State.PENDING) {
      return false
    }

    result = error
    state = Promise.State.REJECTED

    val rejected = this.rejected
    clearHandlers()
    if (rejected != null) {
      if (!isObsolete(rejected)) {
        rejected(error)
      }
    }
    else if (error !is MessageError) {
      LOG.error(error.getMessage(), error)
    }
    return true
  }

  private fun clearHandlers() {
    done = null
    rejected = null
  }

  override fun processed(processed: (T) -> Unit): Promise<T> {
    done(processed)
    rejected({
      processed(null)
    })
    return this
  }
}

fun <T> isObsolete(consumer: ((T) -> Unit)?) = consumer is Obsolescent && consumer.isObsolete()