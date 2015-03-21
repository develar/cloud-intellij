package org.eclipse.flux.client.util

import java.util.HashSet

/**
 * Concrete implementation of Observable that provides a 'setter' method
 * to change the current value.
 */
public class ObservableState<T>(private var value: T) : Observable<T> {
  private var listeners: MutableCollection<Listener<T>>? = null

  init {
    // Initialization is treated as a change event
    notifyNewValue(value)
  }

  SuppressWarnings("unchecked")
  private fun notifyNewValue(value: T) {
    var listeners: Array<Listener<T>>? = null
    synchronized (this) {
      if (this.listeners != null) {
        listeners = this.listeners!!.copyToArray()
      }
    }
    //Careful... we want to make sure we notify listeners outside sync block!
    // TODO: actually it would be better to notify listeners in a separate thread/stack.
    //       as we do not know which locks might be held by code calling into
    //       to the 'setValue'.
    if (listeners != null) {
      for (l in listeners!!) {
        notifyNewValue(l, value)
      }
    }
  }

  private fun notifyNewValue(l: Listener<T>, value: T) {
    l.newValue(this, value)
  }

  override fun getValue(): T {
    return value
  }

  synchronized public fun setValue(v: T) {
    synchronized (this) {
      if (equal(this.value, v)) {
        return
      }
      this.value = v
    }
    notifyNewValue(v)
  }

  private fun equal(a: T, b: T): Boolean {
    if (a == null) {
      return b == null
    }
    else {
      return a == b
    }
  }

  override fun addListener(l: Listener<T>) {
    synchronized (this) {
      if (this.listeners == null) {
        this.listeners = createCollection()
      }
      this.listeners!!.add(l)
    }
    notifyNewValue(l, value)
  }

  private fun createCollection(): HashSet<Listener<T>> {
    return HashSet()
  }

  synchronized override fun removeListener(l: Listener<T>) {
    if (this.listeners != null) {
      this.listeners!!.remove(l)
    }
  }
}