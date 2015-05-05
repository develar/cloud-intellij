package org.jetbrains.json

public trait MessageWriter {
  public fun name(name: String)

  public fun beginArray()
  public fun endArray()

  public fun beginObject()
  public fun endObject()

  fun mark(name: kotlin.String? = null)
  fun reset()

  public val out: ByteArrayUtf8Writer
}

public trait PrimitiveWriter : MessageWriter {
  fun charSequence(name: String, value: CharSequence?)
  fun long(name: String, value: Long)
  fun int(name: String, value: Int)
  fun bool(name: String, value: Boolean)

  public final fun String.invoke(value: CharSequence?) {
    charSequence(this, value)
  }

  public final fun String.invoke(value: Boolean) {
    bool(this, value)
  }

  public final fun String.invoke(value: Long) {
    long(this, value)
  }

  public final fun String.invoke(value: Int) {
    int(this, value)
  }
}

trait MapMemberWriter : PrimitiveWriter {
  /**
   * If your writer throws error, out will not be corrupted (all written bytes will be discarded)
   */
  public inline final fun map(name: String, f: MapMemberWriter.() -> Unit) {
    _map(name, f)
  }

  public inline final fun array(name: String, f: ArrayMemberWriter.() -> Unit) {
    _array(name, f)
  }

  public inline final fun string(name: String, f: () -> CharSequence?) {
    charSequence(name, f())
  }

  public inline final fun String.invoke(f: () -> CharSequence?) {
    charSequence(this, f())
  }
}

trait ArrayMemberWriter : MessageWriter {
  fun string(value: CharSequence)

  public fun int(value: Int)

  public inline final fun map(f: MapMemberWriter.() -> Unit) {
    _map(null, f)
  }

  public inline final fun array(f: ArrayMemberWriter.() -> Unit) {
    _array(null, f)
  }

  public final fun CharSequence.plus() {
    string(this)
  }

  // kotlin bug, doesn't work
//  public final fun Int.plus() {
//    int(this)
//  }
}

private inline fun MessageWriter._map(name: String? = null, f: MapMemberWriter.() -> Unit) {
  mark(name)

  var success = false
  try {
    beginObject()
    (this as MapMemberWriter).f()
    success = true
  }
  finally {
    if (!success) {
      reset()
    }
  }

  endObject()
}

private inline fun MessageWriter._array(name: String?, f: ArrayMemberWriter.() -> Unit) {
  if (name != null) {
    name(name)
  }

  beginArray()
  (this as ArrayMemberWriter).f()
  endArray()
}