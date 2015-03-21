package org.eclipse.flux.core

import org.eclipse.flux.core.KeepAliveConnector

public class KeepAliveConnector(private val channelSwitcher: ChannelSwitcher, private val messageConnector: MessageConnector, private val serviceTypeId: String, private val keepAliveDelay: Long = KeepAliveConnector.KEEP_ALIVE_DELAY, private val keepAliveResponseTimeout: Long = KeepAliveConnector.KEEP_ALIVE_RESPONSE_WAIT_TIME) {
  private val executor: ScheduledExecutorService
  private var scheduledKeepAliveMessage: ScheduledFuture<*>? = null
  private var scheduledShutDown: ScheduledFuture<*>? = null
  private val messageHandlers = listOf<BaseMessageHandler>()
  private val keepAliveResponseHandler = object : BaseMessageHandler(SERVICE_REQUIRED_RESPONSE) {
    public fun handle(messageType: String, message: JSONObject) {
      unsetScheduledShutdown()
      setKeepAliveDelayedMessage()
    }
  }
  private val channelListener = object : ChannelListener() {
    public fun connected(userChannel: String) {
      unsetScheduledShutdown()
      unsetKeepAliveDelayedMessage()
      setKeepAliveDelayedMessage()
    }

    public fun disconnected(userChannel: String) {
      unsetScheduledShutdown()
      unsetKeepAliveDelayedMessage()
      /*
			 * If no channel is connected than keep alive broadcast will
			 * definitely not get any replies and shutdown would occur
			 */
      setKeepAliveDelayedMessage()
    }
  }

  {
    this.executor = Executors.newScheduledThreadPool(2)
    this.messageConnector.addMessageHandler(keepAliveResponseHandler)
    setKeepAliveDelayedMessage()
    messageConnector.addChannelListener(channelListener)
  }

  synchronized private fun unsetKeepAliveDelayedMessage() {
    if (scheduledKeepAliveMessage != null && !scheduledKeepAliveMessage!!.isCancelled()) {
      scheduledKeepAliveMessage!!.cancel(false)
    }
  }

  synchronized private fun unsetScheduledShutdown() {
    if (scheduledShutDown != null && !scheduledShutDown!!.isCancelled()) {
      scheduledShutDown!!.cancel(false)
    }
  }

  synchronized private fun setKeepAliveDelayedMessage() {
    scheduledKeepAliveMessage = this.executor.schedule(object : Runnable {
      override fun run() {
        setScheduledShutdown()
      }
    }, keepAliveDelay, TimeUnit.SECONDS)
  }

  synchronized private fun setScheduledShutdown() {
    try {
      scheduledShutDown = executor.schedule(object : Runnable {
        override fun run() {
          /*
					 * Clean up needs to be done right before disconnecting from
					 * channel would work here
					 */
          //mc.connectChannel(Constants.SUPER_USER);
          System.exit(0)
        }
      }, keepAliveResponseTimeout, TimeUnit.SECONDS)
      val message = JSONObject()
      message.put("username", channelSwitcher.channel)
      message.put("service", serviceTypeId)
      messageConnector.send(SERVICE_REQUIRED_REQUEST, message)
    }
    catch (e: Exception) {
      e.printStackTrace()
    }

  }

  public fun dispose() {
    messageConnector.removeChannelListener(channelListener)
    unsetScheduledShutdown()
    unsetKeepAliveDelayedMessage()
    executor.shutdown()
    messageConnector.removeMessageHandler(keepAliveResponseHandler)
    for (messageHandler in messageHandlers) {
      messageConnector.removeMessageHandler(messageHandler)
    }
  }

  default object {

    private val SERVICE_REQUIRED_REQUEST = "serviceRequiredRequest"
    private val SERVICE_REQUIRED_RESPONSE = "serviceRequiredResponse"

    private val KEEP_ALIVE_DELAY = 3 * 60 * 60.toLong() // 3 hours
    private val KEEP_ALIVE_RESPONSE_WAIT_TIME = 5 // 5 seconds
  }

}
