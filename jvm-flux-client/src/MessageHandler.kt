package org.eclipse.flux.client

public trait MessageHandler {
  public fun handle(type: String, replyTo: String, messageId: String, message: Map<String, Any>)
}