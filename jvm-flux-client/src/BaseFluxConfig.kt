package org.eclipse.flux.client

public abstract class BaseFluxConfig(public override val username: String) : FluxConfig {
  override fun toString(): String {
    return javaClass.getName() + "(" + username + ")"
  }
}