package org.eclipse.flux.core

/**
 * Wrapper around a [MessageConnector] which ensures we are only connected to
 * a single channel at a time. Provides a method to switch channels.
 */
public class ChannelSwitcher(private val messageConnector: MessageConnector) {
  public var channel: String? = null
    private set

  synchronized
  public fun switchToChannel(newChannel: String) {
    if (channel != null) {
      //TODO: use disconnectFromChannelSync (but its not implemented yet)
      messageConnector.disconnectFromChannel(channel!!)
    }
    messageConnector.connectToChannelSync(newChannel)
    channel = newChannel
  }
}