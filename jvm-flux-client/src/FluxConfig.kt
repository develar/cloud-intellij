package org.eclipse.flux.client

import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService

private val LOG = LoggerFactory.getLogger("flux-client")

/**
 * FluxConfig contains information needed to create and configure
 * connection to flux bus.

 * TODO: something about this interface feels iffy. To put a finger on it...
 * in cf-deployer application we need to pass this kind of info around
 * around, but without the value of 'user' being set.
 * I.e. the app will deal with multiple users (as these users connect to the app)
 * and needs something to be able to create fluxconfig objects for each user.
 */
public trait FluxConfig {
  /**
   * Every flux MessageConnector is 'owned' by a specific Flux user. This method
   * returns the Flux user id associated with the flux connections that will be
   * created from this config.
   */
  public val username: String

  /**
   * Connects to flux bus and blocks until a connection is established or failed
   */
  public fun connect(executor: ExecutorService): MessageConnector
}