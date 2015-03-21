package org.eclipse.flux.client

import org.eclipse.flux.client.util.getMessage

/**
 * An immutable object that represent the 'status' of a Flux Messaging Connector.

 * All connection statuses are created starting from the 'INITIALIZING_STATUS' by
 * calling methods that create new status as a transition from a previous
 * status.
 */
/**
 * If a connection is closed because of an error then a Throwable may be attached to
 * track the cause.
 */
public class ConnectionStatus private(private val kind: ConnectionStatus.Kind, private val error: Throwable?) {
  companion object {
    public val INITIALIZING: ConnectionStatus = ConnectionStatus(Kind.INITIALIZING, null)
  }

  enum class Kind {
    INITIALIZING
    CONNECTED
    CLOSED
  }

  override fun toString(): String {
    val s = StringBuilder("ConnectionStatus(")
    s.append(kind)
    if (error != null) {
      s.append(" = ")
      s.append(getMessage(error))
      s.append(")")
    }
    return s.toString()
  }

  /**
   * Transition to 'connected' state. Called when a connection was successfully
   * established. Any prior error info is cleared at this point.
   */
  public fun connect(): ConnectionStatus {
    return ConnectionStatus(Kind.CONNECTED, null)
  }

  /**
   * Transition from current state to error state.
   */
  public fun error(error: Throwable): ConnectionStatus {
    return ConnectionStatus(Kind.CLOSED, error)
  }

  /**
   * Connector may try to reconnect after an error. It will transition into
   * 'initializing state but retains the error so that someone examining
   * the status may be able to determine an error has occurred (and what it was).
   */
  public fun reconnect(): ConnectionStatus {
    return ConnectionStatus(Kind.INITIALIZING, error)
  }

  /**
   * Transition to closed state.
   */
  public fun close(): ConnectionStatus {
    return ConnectionStatus(Kind.CLOSED, error)
  }

  public fun isAuthFailure(): Boolean {
//    if (error is SocketIOException) {
//      val e = error as SocketIOException
//      val msg = e.getMessage()
//      return msg != null && msg!!.contains("handshaking")
//    }
    return false
  }

  public fun isConnected(): Boolean {
    return kind == Kind.CONNECTED
  }
}