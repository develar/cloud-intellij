/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc. and others.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0
 * (http://www.eclipse.org/legal/epl-v10.html), and the Eclipse Distribution
 * License v1.0 (http://www.eclipse.org/org/documents/edl-v10.html).

 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 */
package org.eclipse.flux.client.util

/**
 * An 'Observable' is a value that may change over time. The Observable interface provides
 * a way to observe the current value as well as attach listeners (Observers) that notified
 * when the value changes.
 */
public trait Observable<V> {
  /**
   * Retrieves the current value.
   */
  public fun getValue(): V

  public fun addListener(l: Listener<V>)

  public fun removeListener(l: Listener<V>)
}
