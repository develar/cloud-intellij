/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html).

 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 */
package org.eclipse.flux.client

import org.eclipse.flux.client.SingleResponseHandler

/**
 * This class provides a easier way to handle responses if you are content with
 * just getting the first response to your request.
 *
 *
 * It handles thread synchronization and will timeout if no response arrives within
 * some time limit (currently the timeout is fixed to 1000 milliseconds.
 *
 *
 * Typical use:
 *
 *
 * SingleResponseHandler resp = new FluxResponseHandler(conn, "getProjectsResponse", "userName") {
 * protected ProjectList parse(JSONObject message) throws Exception {
 * ... extract ProjectList from message...
 * ... throw some exception if message doesn't parse...
 * }
 * }
 * conn.send(...send the request...);
 * return resp.awaitResult();
 *
 *
 * WARNING: SingleResponseHandler is convenient but it is also arguably somewhat
 * of an 'anti-pattern' in Flux because generally multiple parties may respond
 * to a request and the requestor normally should handle that or risk having
 * incomplete or outdated information.
 */
public abstract class SingleResponseHandler<T>(private var conn: MessageConnector?, messageType: String) : BaseMessageHandler(messageType) {
  private val timeoutStarted = AtomicBoolean(false)
  private val future: BasicFuture<T> // the result goes in here once we got it.

  private fun cleanup() {
    val c = this.conn // local var: thread safe
    if (c != null) {
      this.conn = null
      c.removeMessageHandler(this)
    }
  }

  init {
    this.future = BasicFuture<T>()
    this.future.whenDone(object : Runnable {
      override fun run() {
        cleanup()
      }
    })
    conn!!.addMessageHandler(this)
  }

  override fun handle(type: String, message: Map<String, Any>) {
    try {
      errorParse(message)
      future.resolve(parse(type, message))
    }
    catch (e: Throwable) {
      future.reject(e)
    }
  }

  /**
   * Should inspect the message to determine if it is an 'error'
   * response and throw an exception in that case. Do nothing otherwise.
   */
  protected fun errorParse(message: Map<String, Any>) {
    val error = message.get(ERROR) as String?
    if (error != null) {
      val errorDetails = message.get("errorDetails") as String?
      if (errorDetails != null) {
        LOG.error(errorDetails)
      }
      throw Exception(error)
    }
  }

  protected abstract fun parse(messageType: String, message: Map<String, Any>): T

  private fun ensureTimeout() {
    if (!future.isDone() && TIME_OUT > 0 && timeoutStarted.compareAndSet(false, true)) {
      timer().schedule(object : TimerTask() {
        override fun run() {
          try {
            future.reject(TimeoutException())
          }
          catch (e: Throwable) {
            //don't let Exception fly.. or the timer thread will die!
            e.printStackTrace()
          }

        }
      }, TIME_OUT)
    }
  }

  public fun getFuture(): BasicFuture<T> {
    ensureTimeout()
    return future
  }

  default object {
    public val USERNAME: String = "username"

    /**
     * Positive timeout in milliseconds. Negative number or 0 means 'infinite'.
     */
    private val TIME_OUT = (30 * 1000).toLong() //quite long for now, for debugging purposes

    private var timer: Timer? = null

    /**
     * Timer thread shared between all 'FluxResponseHandler' to handle timeouts.
     */
    synchronized private fun timer(): Timer {
      if (timer == null) {
        timer = Timer(javaClass<SingleResponseHandler<Any>>().getName() + "_TIMER", true)
      }
      return timer!!
    }
  }
}
