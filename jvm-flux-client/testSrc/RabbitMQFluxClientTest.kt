/**
 * ****************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html).
 *
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 * *****************************************************************************
 */
package org.eclipse.flux.client.java

class RabbitMQFluxClientTest : AbstractFluxClientTest() {
  /**
   * A Receiver connects to flux message bus and adds message handler
   * to recieve a single message asynchronously. Once message is
   * received it disconnects.
   */
  abstract inner class Receiver<T> [throws(javaClass<Exception>())]
  (public val user: String, type: String, channel: String = user) : MessageHandler(type) {
    private val flux: MessageConnector
    public val result: BasicFuture<T>

    {
      flux = createConnection(user)
      flux.connectToChannelSync(channel)
      flux.addMessageHandler(this)
      result = BasicFuture<T>()
      result.setTimeout(AbstractFluxClientTest.TIMEOUT)
      result.whenDone(object : Runnable {
        override fun run() {
          flux.disconnect()
        }
      })
    }

    public fun handle(type: String, message: JSONObject) {
      try {
        result.resolve(receive(type, message))
      }
      catch (e: Throwable) {
        result.reject(e)
      }

    }

    throws(javaClass<Throwable>())
    protected abstract fun receive(type: String, message: JSONObject): T

    throws(javaClass<Exception>())
    public fun get(): T {
      return result.get()
    }

    public fun future(): BasicFuture<T> {
      return result
    }
  }

  private val client = FluxClient.DEFAULT_INSTANCE

  throws(javaClass<Exception>())
  public fun testConnectAndDisconnect() {
    val conn = createConnection("Bob")
    conn.disconnect()
  }

  throws(javaClass<Exception>())
  public fun testSendAndReceive() {
    val sender = object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        send("bork", JSONObject().put(USERNAME, "Bob").put("msg", "Hello"))
        return null
      }
    }

    val receiver = object : AbstractFluxClientTest.Process<String>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): String {
        val msg = areceive("bork")
        sender.start()
        return msg.get().getString("msg")
      }
    }

    receiver.start()        //only start receiver (not sender).
    // receiver should start sender at the right time
    // to avoid race condition between them.
    await(sender, receiver)
    assertEquals("Hello", receiver.result.get())
  }

  /**
   * Test that messages sent to '*' user are received by multiple users.
   */
  throws(javaClass<Exception>())
  public fun testMessageToEveryOne() {

    val users = array("Bob", "Alice", "Sender", MessageConstants.SUPER_USER)

    //First create all the receivers (will register the message handlers to receive messages
    // this must be done before starting the sender to avoid a race condition.
    val receivers = ArrayList<Receiver<String>>()
    for (user in users) {
      receivers.add(object : Receiver<String>(user, "bork") {
        throws(javaClass<Throwable>())
        override fun receive(type: String, message: JSONObject): String {
          return message.getString("msg")
        }
      })
    }

    //Run the process that sends messages.
    run(object : AbstractFluxClientTest.Process<Void>("Sender") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        send("bork", JSONObject().put(USERNAME, "*") //send to all users.
                .put("msg", "Hello"))
        return null
      }
    })

    //Now check that each receiver got the message
    for (r in receivers) {
      //Yes "Sender" receiving messages from 'himself' because they are sent using a different MessageConnector.
      // The rule about not delivering messages to their 'origin' applies at the MessageConnector level, not
      // the user level. Two message connectors for the same user count as different origins.
      TestCase.assertEquals("Hello", r.get())
    }
  }

  /**
   * Tests that messages are not delivered back to their origin.
   */
  throws(javaClass<Exception>())
  public fun testSelfSending() {
    val messageType = "bork"

    val receiver = receiver("Bob", messageType)

    run(object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        val received = areceive(messageType)
        send(messageType, JSONObject().put(USERNAME, "Bob").put("msg", "Hello"))
        //message sent by this process (i.e same messageConnector) is not received.
        AbstractFluxClientTest.assertError<Any>(javaClass<TimeoutException>(), received)
        return null
      }
    })

    //Message should still be delivered to another messageconnector for the same user.
    TestCase.assertEquals("Hello", receiver.get())
  }

  /**
   * Super user, connected to super user channel receives messages from everyone.
   */
  throws(javaClass<Exception>())
  public fun testSuperReceiver() {
    val root = receiver(SUPER_USER, "bork")
    val bob = receiver("Bob", "bork")
    val alice = receiver("Alice", "bork")

    run(object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        send("bork", JSONObject().put(USERNAME, "Bob").put("msg", "Message from Bob"))
        return null
      }
    })

    assertEquals("Message from Bob", root.result.get())
    assertEquals("Message from Bob", bob.result.get())
    AbstractFluxClientTest.assertError<Any>(javaClass<TimeoutException>(), alice.future()) //Alice is not super user shouldn't get Bob's message.
  }

  /**
   * Super user can connect to specific user's channel and only get messages from that user.
   */
  throws(javaClass<Exception>())
  public fun testSuperUserConnectToOtherChannel() {
    val root = receiver(SUPER_USER, "bork", "Bob") // connect "super" receiver to "Bob" channel
    val bob = receiver("Bob", "bork")

    run(object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        send("bork", JSONObject().put(USERNAME, "Bob").put("msg", "Message from Bob"))
        return null
      }
    })

    assertEquals("Message from Bob", root.result.get())
    assertEquals("Message from Bob", bob.result.get())
  }

  /**
   * Super user can connect to specific user's channel and will not get messages from other users.
   */
  throws(javaClass<Exception>())
  public fun testSuperUserConnectToOtherChannel2() {
    val root = receiver(SUPER_USER, "bork", "Alice") // connect "super" receiver to "Alice" channel
    val bob = receiver("Bob", "bork")

    run(object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        send("bork", JSONObject().put(USERNAME, "Bob").put("msg", "Message from Bob"))
        return null
      }
    })

    AbstractFluxClientTest.assertError<Any>(javaClass<TimeoutException>(), root.future())
    assertEquals("Message from Bob", bob.result.get())
  }

  throws(javaClass<Exception>())
  public fun testRequestResponsePattern() {
    val serviceStarted = BasicFuture<Void>()
    val quitRequested = BasicFuture<Void>()

    val service = object : AbstractFluxClientTest.Process<Void>(SUPER_USER) {
      throws(javaClass<Exception>())
      override fun execute(): Void {
        conn.addMessageHandler(object : RequestResponseHandler(conn, "helloRequest") {
          throws(javaClass<Exception>())
          protected fun fillResponse(type: String, req: JSONObject, res: JSONObject): JSONObject {
            val cmd = req.getString("cmd")
            if ("quit" == cmd) {
              quitRequested.resolve(null)
              //Note this response may not be delivered because the service may shut down before
              // the message is sent.
              return res.put("msg", "Quit request received from " + req.getString(USERNAME))
            }
            else if ("greeting" == cmd) {
              return res.put("msg", "Hello " + req.getString(USERNAME))
            }
            else {
              throw IllegalArgumentException("Unknown command: " + cmd)
            }
          }
        })
        serviceStarted.resolve(null)

        //Wait for quit request
        return quitRequested.get()
      }
    }

    val bob = object : AbstractFluxClientTest.Process<Void>("Bob") {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        //Bob has to wait for the service to be ready before sending requests to it!
        serviceStarted.get()
        try {
          TestCase.assertEquals("Hello Bob", sendRequest("helloRequest", JSONObject().put(USERNAME, "Bob").put("cmd", "greeting"), object : AbstractFluxClientTest.ResponseHandler<String>() {
            throws(javaClass<Exception>())
            override fun handle(messageType: String, msg: JSONObject): String {
              return msg.getString("msg")
            }
          }))

          AbstractFluxClientTest.assertError<Any>("bogusCommand", asendRequest("helloRequest", JSONObject().put(USERNAME, "Bob").put("cmd", "bogusCommand"), object : AbstractFluxClientTest.ResponseHandler<String>() {
            throws(javaClass<Exception>())
            override fun handle(messageType: String, msg: JSONObject): String {
              return msg.getString("msg")
            }
          }))
        }
        finally {
          conn.send("helloRequest", JSONObject().put(USERNAME, "Bob").put("cmd", "quit"))
        }
        return null
      }
    }

    run(service, bob)
  }

  /**
   * Test that super user can connect and disconnect channels to
   * switch between users.
   *
   *
   * This basic test only swtiches channels and verifies the 'isConnected'
   * state follows suite. It does not verify whether message delivery is
   * changed according connected channels.
   */
  throws(javaClass<Exception>())
  public fun testBasicChannelSwitching() {
    val root = object : AbstractFluxClientTest.Process<Void>(SUPER_USER) {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        TestCase.assertTrue(conn.isConnected(SUPER_USER))

        conn.disconnectFromChannelSync(SUPER_USER)
        conn.connectToChannelSync("Bob")
        TestCase.assertFalse(conn.isConnected(SUPER_USER))
        TestCase.assertTrue(conn.isConnected("Bob"))

        conn.disconnectFromChannelSync("Bob")
        conn.connectToChannelSync("Alice")
        TestCase.assertFalse(conn.isConnected("Bob"))
        TestCase.assertTrue(conn.isConnected("Alice"))

        return null
      }
    }
    run(root)
  }

  /**
   * Test that super user can connect and disconnect channels to
   * switch between users.
   *
   *
   * This basic test only swtiches channels and verifies the 'isConnected'
   * state follows suite. It does not verify whether message delivery is
   * changed according connected channels.
   */
  throws(javaClass<Exception>())
  public fun testChannelSwitchingMessageReception() {

    val users = array("Bob", "Alice")

    /**
     * Resolves when the root process is connected to corresponding channel
     */
    val channelSynch = ArrayList<BasicFuture<Void>>()
    for (user in users) {
      channelSynch.add(BasicFuture<Void>())

      val userProcess = object : AbstractFluxClientTest.Process<Void>(user) {
        throws(javaClass<Exception>())
        override fun execute(): Void? {
          for (i in users.indices) {
            val currentChannel = users[i]
            channelSynch.get(i).get() //wait for root to switch to this channel.
            send("bork", JSONObject().put(USERNAME, user).put("msg", user + " -> " + currentChannel))
          }
          return null
        }
      }
      userProcess.start()
    }

    val root = object : AbstractFluxClientTest.Process<List<String>>(SUPER_USER) {
      throws(javaClass<Exception>())
      override fun execute(): List<String> {
        val receivedMessages = ArrayList<String>()
        TestCase.assertTrue(conn.isConnected(SUPER_USER))
        conn.disconnectFromChannelSync(SUPER_USER)
        TestCase.assertFalse(conn.isConnected(SUPER_USER))

        conn.addMessageHandler(object : MessageHandler("bork") {
          public fun handle(type: String, message: JSONObject) {
            try {
              receivedMessages.add(message.getString("msg"))
            }
            catch (e: Exception) {
              e.printStackTrace()
            }

          }
        })

        for (i in users.indices) {
          val currentChannel = users[i]
          if (i > 0) {
            val previousChannel = users[i - 1]
            System.out.println("Previous channel = " + previousChannel)
            conn.disconnectFromChannelSync(previousChannel)
          }
          conn.connectToChannelSync(currentChannel)

          //Check channel connected state(s)
          for (channel in users) {
            val isConnected = conn.isConnected(channel)
            TestCase.assertEquals("[" + currentChannel + "] " + channel + " isConnected? " + isConnected, channel == currentChannel, isConnected)
          }

          channelSynch.get(i).resolve(null)
          Thread.sleep(500) //Allow some time for user processes to send messages while root is connected to this channel.
        }

        return receivedMessages
      }
    }

    run(root)

    val results = root.result.get()
    val expectedMessages = arrayOfNulls<String>(users.size())
    for (i in expectedMessages.indices) {
      expectedMessages[i] = users[i] + " -> " + users[i]
    }
    assertArrayEquals(expectedMessages, results.toArray())
  }

  /**
   * Use asynchronous connectToChannel and disconnectFromChannel together with channel listener
   * to avoid race condition when sending / receiving messages.
   */
  throws(javaClass<Exception>())
  public fun testChannelListener() {
    val channels = array(SUPER_USER, "Bob", "Alice")

    val echoServiceReady = BasicFuture()
    val theEnd = BasicFuture<Void>() //resolves at end of callback spagetti sequence

    val root = object : AbstractFluxClientTest.Process<List<String>>(SUPER_USER) {
      throws(javaClass<Exception>())
      override fun execute(): List<String> {
        val receivedMessages = ArrayList<String>()

        echoServiceReady.get()

        conn.addMessageHandler(object : MessageHandler("echoResponse") {
          public fun handle(type: String, message: JSONObject) {
            try {
              receivedMessages.add(message.getString("msg"))
            }
            catch (e: Exception) {
              e.printStackTrace()
            }

          }
        })

        conn.addChannelListener(object : IChannelListener {
          override fun disconnected(oldChannel: String) {
            val newChannel = nextChannel(oldChannel)
            if (newChannel != null) {
              conn.connectToChannel(newChannel)
            }
            else {
              theEnd.resolve(null)
            }
          }

          override fun connected(currentChannel: String) {
            try {
              send("echoRequest", JSONObject().put(USERNAME, currentChannel).put("msg", "Hello on channel " + currentChannel))
              //Give some time for response
              setTimeout(500, object : TimerTask() {
                override fun run() {
                  try {
                    conn.disconnectFromChannel(currentChannel)
                  }
                  catch (e: Exception) {
                    e.printStackTrace()
                  }

                }
              })
            }
            catch (e: Exception) {
              e.printStackTrace()
            }

          }

          private fun nextChannel(oldChannel: String): String? {
            for (i in 0..channels.size() - 1 - 1) {
              if (channels[i] == oldChannel) {
                return channels[i + 1]
              }
            }
            return null
          }
        })

        conn.disconnectFromChannel(channels[0])
        theEnd.get() // must wait until callback spagetti finishes before allowing this process to terminate.

        return receivedMessages
      }
    }
    val echoService = object : AbstractFluxClientTest.Process<Void>(SUPER_USER) {
      throws(javaClass<Exception>())
      override fun execute(): Void? {
        conn.addMessageHandler(object : RequestResponseHandler(conn, "echoRequest")//no need to override anything. The default implementation is already a 'echo' service.
        )
        echoServiceReady.resolve(null)
        await(root) //once main process finished we can finish too
        return null
      }
    }

    run(echoService, root)

    val expected = ArrayList<String>()
    for (i in 1..channels.size() - 1) {
      expected.add("Hello on channel " + channels[i])
    }
    assertArrayEquals(expected.toArray(), root.result.get().toArray())
  }

  //TODO: RequestResponseHandler uses callback id properly.

  //	@Override
  //	public void handle(String type, JSONObject message) {
  //		String command = message.getString("command");
  //		if ("quit".equals("command")) {
  //			quit.resolve(null);
  //		}
  //		message()
  //	}

  //TODO: testing direct request response pattern.

  //TODO: normal user can only send messages to their own channel.

  //TODO: tests for SingleResponseHandler. Check that it properly cleans up (uregisteres message handler
  //   when response is received, when timed out etc.


  /**
   * Create a simple single response receiver that does these steps:
   * - open message connector for some 'user'
   * - connect to a 'channel' (may be different from the user)
   * - wait for a message of 'messageType'
   * - return and retrieve the property 'msg' from the receive message object
   * - disconnect from the flux message bus.
   */
  throws(javaClass<Exception>())
  private fun receiver(user: String, messageType: String, channel: String): Receiver<String> {
    return object : Receiver<String>(user, messageType, channel) {
      throws(javaClass<Throwable>())
      override fun receive(type: String, message: JSONObject): String {
        return message.getString("msg")
      }
    }
  }

  /**
   * Convenience method to create single response receiver where channelName = userName
   */
  throws(javaClass<Exception>())
  private fun receiver(user: String, messageType: String): Receiver<String> {
    return receiver(user, messageType, user)
  }

  throws(javaClass<Exception>())
  override fun createConnection(user: String): MessageConnector {
    return RabbitMQFluxConfig(user).connect(client)
  }
}

