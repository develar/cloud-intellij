package org.eclipse.flux.client.util

import java.io.ByteArrayOutputStream
import java.io.PrintStream

public fun getDeepestCause(e: Throwable): Throwable {
  var cause = e
  var parent: Throwable? = e.getCause()
  while (parent != null && parent != e) {
    cause = parent!!
    parent = cause.getCause()
  }
  return cause
}

public fun getMessage(e: Throwable): String {
  // The message of nested exception is usually more interesting than the
  // one on top.
  val cause = getDeepestCause(e)
  val msg = cause.javaClass.getSimpleName() + ": " + cause.getMessage()
  return msg
}

public fun stackTrace(e: Throwable): String {
  try {
    val trace = ByteArrayOutputStream()
    val dump = PrintStream(trace, true, "utf8")
    e.printStackTrace(dump)
    return trace.toString("utf8")
  } catch (shouldNotHappen: Exception) {
    throw RuntimeException(shouldNotHappen)
  }

}

public fun exception(error: Throwable): Exception {
  if (error is Exception) {
    return error : Exception
  } else {
    return RuntimeException(error)
  }
}

public fun unchecked(e: Exception): RuntimeException {
  if (e is RuntimeException) {
    return e : RuntimeException
  }
  return RuntimeException(e)
}