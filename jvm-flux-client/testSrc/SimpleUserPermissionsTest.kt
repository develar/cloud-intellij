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

public class SimpleUserPermissionsTest : TestCase() {

  fun conf(user: String): FluxConfig {
    //Mock Flux config
    return object : FluxConfig {
      public fun toSocketIO(): SocketIOFluxConfig? {
        return null
      }

      override fun getUser(): String {
        return user
      }

      throws(javaClass<Exception>())
      override fun connect(fluxClient: FluxClient): MessageConnector {
        throw Error("Not impelemented")
      }

      override fun permissions(): UserPermissions {
        return SimpleUserPermissions(this)
      }
    }
  }

  throws(javaClass<Exception>())
  public fun testSuperCanJoinSuper() {
    val user = MessageConstants.SUPER_USER
    val channel = MessageConstants.SUPER_USER
    join(user, channel)
  }

  throws(javaClass<Exception>())
  public fun testUserCanJoinSelf() {
    val user = "testUser"
    val channel = "testUser"
    join(user, channel)
  }

  throws(javaClass<Exception>())
  public fun testUserCanNotJoinSuper() {
    val user = "testUser"
    val channel = MessageConstants.SUPER_USER
    joinShouldFail(user, channel)
  }

  throws(javaClass<Exception>())
  public fun testUserCannotJoinOtherUser() {
    joinShouldFail("bob", "john")
  }

  private fun joinShouldFail(user: String, channel: String) {
    try {
      join(user, channel)
      TestCase.fail("Should have rejected '" + user + "' to join '" + channel)
    }
    catch (e: Exception) {
      TestCase.assertEquals("'" + user + "' is not allowed to join channel '" + channel + "'", e.getMessage())
    }

  }

  throws(javaClass<Exception>())
  private fun join(user: String, channel: String) {
    val conf = conf(user)
    val auth = conf.permissions()
    auth.checkChannelJoin(channel)
  }

}
