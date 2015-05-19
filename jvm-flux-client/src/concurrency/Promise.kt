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

public trait Promise<T> {
  public enum class State {
    PENDING
    FULFILLED
    REJECTED
  }

  public val state: State

  public fun done(done: (T) -> Unit): Promise<T>

  public fun processed(fulfilled: AsyncPromise<T>): Promise<T>

  public fun rejected(rejected: (Throwable) -> Unit): Promise<T>

  public fun processed(processed: (T) -> Unit): Promise<T>

  public fun <SUB_RESULT> then(done: (T) -> SUB_RESULT): Promise<SUB_RESULT>

  public fun <SUB_RESULT> then(done: AsyncFunction<T, SUB_RESULT>): Promise<SUB_RESULT>

  fun notify(child: AsyncPromise<T>)

  companion object {
    public val DONE: Promise<Any?> = DonePromise(null)
    public val REJECTED: Promise<Any> = RejectedPromise(createError("rejected"))

    public fun createError(error: String): RuntimeException {
      return MessageError(error)
    }

    public fun <T> resolve(result: T): Promise<T> {
      if (result == null) {
        @suppress("UNCHECKED_CAST")
        return DONE as Promise<T>
      }
      else {
        return DonePromise(result)
      }
    }

    public fun <T> reject(error: String): Promise<T> {
      return reject(createError(error))
    }

    public fun <T> reject(error: Throwable?): Promise<T> {
      if (error == null) {
        @suppress("UNCHECKED_CAST")
        return REJECTED as Promise<T>
      }
      else {
        return RejectedPromise(error)
      }
    }

    public fun <T> all(promises: Collection<Promise<*>>, totalResult: T = null): Promise<T> {
      if (promises.isEmpty()) {
        @suppress("UNCHECKED_CAST")
        return DONE as Promise<T>
      }

      val totalPromise = AsyncPromise<T>()
      val done = CountDownConsumer(promises.size(), totalPromise, totalResult)
      val rejected = {it: Throwable -> Unit
        if (totalPromise.state == Promise.State.PENDING) {
          totalPromise.setError(it)
        }
      }

      for (promise in promises) {
        //noinspection unchecked
        promise.done(done)
        promise.rejected(rejected)
      }
      return totalPromise
    }
  }
}

public class MessageError(error: String) : RuntimeException(error) {
  synchronized public fun fillInStackTrace(): Throwable? {
    return this
  }
}