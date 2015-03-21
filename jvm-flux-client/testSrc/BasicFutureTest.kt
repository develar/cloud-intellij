/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html).

 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 */
package org.eclipse.flux.client.java

public class BasicFutureTest : TestCase() {

  private var timer: Timer? = null

  throws(javaClass<Exception>())
  override fun setUp() {
    timer = Timer()
  }

  throws(javaClass<Exception>())
  override fun tearDown() {
    timer!!.cancel()
  }

  /**
   * Test that setTimeout makes a future reject after some time.
   */
  public fun testTimeout() {
    val f = BasicFuture<Void>()
    f.setTimeout(500)
    try {
      f.get()
      Assert.fail("Should have thrown a TimeoutException")
    }
    catch (e: Throwable) {
      TestCase.assertTrue(ExceptionUtil.getDeepestCause(e) is TimeoutException)
    }

  }

  /**
   * Test that value resolved is returned by get, if resolve is called before get.
   */
  throws(javaClass<Exception>())
  public fun testResolveBeforeGet() {
    val f = BasicFuture<String>()
    f.resolve("foo")
    f.setTimeout(200)
    assertEquals("foo", f.get())
  }

  /**
   * Test that value resolved is returned by get, if resolve is called after get.
   */
  throws(javaClass<Exception>())
  public fun testResolveAfterGet() {
    val f = BasicFuture<String>()
    resolveAfter(f, 200, "foo")
    f.setTimeout(500)
    assertEquals("foo", f.get())
  }

  /**
   * Test that double resolve returns first value
   */
  throws(javaClass<Exception>())
  public fun testDoubleResolve() {
    val f = BasicFuture<String>()
    f.resolve("first")
    f.resolve("second")
    f.setTimeout(500)
    assertEquals("first", f.get())
  }

  /**
   * Test that double resolve returns first value
   */
  throws(javaClass<Exception>())
  public fun testRejectResolve() {
    val f = BasicFuture<String>()
    f.reject(Error("Fail"))
    f.resolve("success")
    f.setTimeout(500)
    TestCase.assertEquals("Fail", getException<Any>(f).getMessage())
  }

  /**
   * Test that double resolve returns first value
   */
  throws(javaClass<Exception>())
  public fun testResolveReject() {
    val f = BasicFuture<String>()
    f.resolve("success")
    f.reject(Error("Fail"))
    f.setTimeout(500)
    assertEquals("success", f.get())
  }

  private fun <T> getException(f: BasicFuture<T>): Throwable {
    try {
      val v = f.get()
      TestCase.fail("Should have thrown an exception but returned " + v)
      throw Error("unreachable code")
    }
    catch (e: Throwable) {
      return ExceptionUtil.getDeepestCause(e)
    }

  }

  private fun <T> resolveAfter(f: BasicFuture<T>, delay: Long, value: T) {
    timer!!.schedule(object : TimerTask() {
      override fun run() {
        f.resolve(value)
      }
    }, delay)
  }
}
