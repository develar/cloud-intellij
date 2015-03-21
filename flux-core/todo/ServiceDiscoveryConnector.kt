package org.eclipse.flux.core

/**
 * Minimal implementation of a Service Connector that is suitable to use in a running instance of
 * Eclipse that a user has started manually and is simply using as their development IDE.
 *
 * This allows other flux clients to discover this service as an available JDT service that
 * is already assigned to the user that is running the eclipse instance.
 */
public class ServiceDiscoveryConnector(private val channelSwitcher: ChannelSwitcher, private var messageConnector: MessageConnector?, private val serviceTypeId: String, keepAlive: Boolean) {
  private val onDispose = ArrayList<Runnable>()

  private val channelListener = object : ChannelListener {
    override public fun connected(userChannel: String) {
      sendStatus(userChannel, "ready")
    }

    override public fun disconnected(userChannel: String) {
      if (messageConnector != null && messageConnector!!.isConnected()) {
        sendStatus(userChannel, "unavailable", "Disconnected")
      }
    }
  }

  init {
    messageConnector!!.addChannelListener(channelListener)

    val userChannel = channelSwitcher.channel
    if (userChannel != null) {
      sendStatus(userChannel, "ready")
    }

    handler(object : BaseMessageHandler("discoverServiceRequest") {
      override public fun handle(type: String, message: Map<String, Any>) {
        val user = channelSwitcher.channel
        if (forMe(message) && (message.get("username") == user || SUPER_USER == user)) {
          messageConnector!!.send("discoverServiceResponse", user!!) {
            it.name("status").value(if (message.get("username") == user) "ready" else "available")
          }
        }
      }
    })

    messageConnector!!.addMessageHandler(object : BaseMessageHandler("startServiceRequest") {
      override public fun handle(type: String, message: Map<String, Any>) {
        messageConnector!!.removeMessageHandler(this)
        val user = message.get("username") as String

        protected val COPY_PROPS: Array<String> = array("service", "requestSenderID", "username", "callback_id")

        val serviceStartedMessage = JSONObject(message, COPY_PROPS)
        messageConnector!!.send("startServiceResponse", user) {
          it.name("service").value(message.get("service"))
        }
        sendStatus(user, "starting")
        channelSwitcher.switchToChannel(user)
      }
    })
  }

  private fun forMe(message: JSONObject): Boolean {
    try {
      return message.getString("service").equals(serviceTypeId)
    }
    catch (e: Throwable) {
      LOG.error(e.getMessage(), e)
      return false
    }

  }

  synchronized private fun handler(h: MessageHandler) {
    onDispose.add(object : Runnable {
      override fun run() {
        messageConnector!!.removeMessageHandler(h)
      }
    })
    messageConnector!!.addMessageHandler(h)
  }

  synchronized public fun dispose() {
    try {
      if (messageConnector != null) {
        sendStatus(channelSwitcher.channel, "unavailable", "Shutdown")
        for (r in onDispose) {
          r.run()
        }
      }
      messageConnector = null
    }
    catch (e: Exception) {
      LOG.error(e.getMessage(), e)
    }
  }

  private fun sendStatus(user: String, status: String, error: String? = null) {
    messageConnector!!.send("serviceStatusChange", user) {
      it.name("service").value(serviceTypeId)
      it.name("status").value(status)
      if (error != null) {
        it.name("error").value(error)
      }
    }
  }
}