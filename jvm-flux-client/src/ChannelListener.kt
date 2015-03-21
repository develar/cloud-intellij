package org.eclipse.flux.client

/**
 * Listener for channel connected/disconnected events
 */
public trait ChannelListener {
  public fun connected(userChannel: String)

  public fun disconnected(userChannel: String)
}