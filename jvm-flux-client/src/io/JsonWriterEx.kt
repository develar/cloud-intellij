/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.json

import java.io.Closeable
import java.io.IOException
import java.lang

public class JsonWriterEx() : Closeable, PrimitiveWriter, MapMemberWriter, ArrayMemberWriter {
  companion object {
    /**
     * An array with no elements requires no separators or newlines before
     * it is closed.
     */
    val EMPTY_ARRAY = 1

    /**
     * A array with at least one value requires a comma and newline before
     * the next element.
     */
    val NONEMPTY_ARRAY = 2

    /**
     * An object with no name/value pairs requires no separators or newlines
     * before it is closed.
     */
    val EMPTY_OBJECT = 3

    /**
     * An object whose most recent element is a key. The next element must
     * be a value.
     */
    val DANGLING_NAME = 4

    /**
     * An object with at least one name/value pair requires a comma and
     * newline before the next element.
     */
    val NONEMPTY_OBJECT = 5

    /**
     * No object or array has been started.
     */
    val EMPTY_DOCUMENT = 6

    /**
     * A document with at an array or object.
     */
    val NONEMPTY_DOCUMENT = 7

    /**
     * A document that's been closed and cannot be accessed.
     */
    val CLOSED = 8

    val REPLACEMENT_CHARS = arrayOfNulls<String>(128)

    val DEFAULT_INDENT: String?

    init {
      for (i in 0..31) {
        REPLACEMENT_CHARS[i] = lang.String.format("\\u%04x", i.toInt())
      }
      REPLACEMENT_CHARS['"'.toInt()] = "\\\""
      REPLACEMENT_CHARS['\\'.toInt()] = "\\\\"
      REPLACEMENT_CHARS['\t'.toInt()] = "\\t"
      REPLACEMENT_CHARS['\b'.toInt()] = "\\b"
      REPLACEMENT_CHARS['\n'.toInt()] = "\\n"
      REPLACEMENT_CHARS['\r'.toInt()] = "\\r"
      REPLACEMENT_CHARS[12] = "\\f"

      val prettyPrint = System.getProperty("json.prettyPrint")
      DEFAULT_INDENT = when (prettyPrint) {
        null -> null
        "" -> "  "
        else -> if (prettyPrint.toBoolean()) "  " else null
      }
    }
  }

  private var stack = IntArray(32)
  private var stackSize = 0
  private var markedStackSize = 0

  override val out = ByteArrayUtf8Writer()

  /**
   * A string containing a full set of spaces for a single level of
   * indentation, or null for no pretty printing.
   */
  private var indent: String? = DEFAULT_INDENT

  /**
   * The name/value separator; either ":" or ": ".
   */
  private var separator = ":"

  /**
   * Configure this writer to relax its syntax rules. By default, this writer
   * only emits well-formed JSON as specified by [RFC 4627](http://www.ietf.org/rfc/rfc4627.txt).
   */
  public var lenient: Boolean = false

  private var deferredName: String? = null

  public var serializeNulls: Boolean = true

  init {
    push(EMPTY_DOCUMENT)
  }

  /**
   * Sets the indentation string to be repeated for each level of indentation
   * in the encoded document. If `indent.isEmpty()` the encoded document
   * will be compact. Otherwise the encoded document will be more
   * human-readable.

   * @param indent a string containing only whitespace.
   */
  public fun setIndent(indent: String) {
    if (indent.length() == 0) {
      this.indent = null
      this.separator = ":"
    }
    else {
      this.indent = indent
      this.separator = ": "
    }
  }

  override fun mark(name: String?) {
    out.mark()
    markedStackSize = stackSize

    if (name != null) {
      this.name(name)
    }
  }

  override fun reset() {
    out.reset()
    stackSize = markedStackSize
  }

  /**
   * Begins encoding a new array. Each call to this method must be paired with
   * a call to [.endArray].

   * @return this writer.
   */
  override fun beginArray() {
    writeDeferredName()
    open(EMPTY_ARRAY, '[')
  }

  /**
   * Ends encoding the current array.

   * @return this writer.
   */
  override fun endArray() {
    close(EMPTY_ARRAY, NONEMPTY_ARRAY, ']')
  }

  /**
   * Begins encoding a new object. Each call to this method must be paired
   * with a call to [.endObject].

   * @return this writer.
   */
  override public fun beginObject() {
    writeDeferredName()
    open(EMPTY_OBJECT, '{')
  }

  /**
   * Ends encoding the current object.

   * @return this writer.
   */
  override public fun endObject() {
    close(EMPTY_OBJECT, NONEMPTY_OBJECT, '}')
  }

  /**
   * Enters a new scope by appending any necessary whitespace and the given
   * bracket.
   */
  private fun open(empty: Int, openBracket: Char): JsonWriterEx {
    beforeValue(true)
    push(empty)
    out.write(openBracket)
    return this
  }

  /**
   * Closes the current scope by appending any necessary whitespace and the
   * given bracket.
   */
  private fun close(empty: Int, nonempty: Int, closeBracket: Char): JsonWriterEx {
    val context = peek()
    if (context != nonempty && context != empty) {
      throw IllegalStateException("Nesting problem")
    }
    if (deferredName != null) {
      throw IllegalStateException("Dangling name: " + deferredName)
    }

    stackSize--
    if (context == nonempty) {
      newline()
    }
    out.write(closeBracket)
    return this
  }

  private fun push(newTop: Int) {
    if (stackSize == stack.size()) {
      val newStack = IntArray(stackSize * 2)
      System.arraycopy(stack, 0, newStack, 0, stackSize)
      stack = newStack
    }
    stack[stackSize++] = newTop
  }

  /**
   * Returns the value on the top of the stack.
   */
  private fun peek(): Int {
    checkIsClosed()
    return stack[stackSize - 1]
  }

  /**
   * Replace the value on the top of the stack with the given value.
   */
  private fun replaceTop(topOfStack: Int) {
    stack[stackSize - 1] = topOfStack
  }

  override fun name(name: String) {
    if (deferredName != null) {
      throw IllegalStateException()
    }
    checkIsClosed()
    deferredName = name
  }

  private fun writeDeferredName() {
    if (deferredName != null) {
      beforeName()
      string(deferredName!!)
      deferredName = null
    }
  }

  override fun charSequence(name: String, value: CharSequence?) {
    beforeName()
    string(name)
    beforeValue(false)
    if (value == null) {
      out.write("null")
    }
    else {
      string(value)
    }
  }

  override fun long(name: String, value: Long) {
    beforeName()
    string(name)
    beforeValue(false)
    out.write(java.lang.Long.toString(value))
  }

  override fun int(name: String, value: Int) {
    beforeName()
    string(name)
    beforeValue(false)
    out.write(java.lang.Integer.toString(value))
  }

  override fun bool(name: String, value: Boolean) {
    beforeName()
    string(name)
    beforeValue(false)
    out.write(if (value) "true" else "false")
  }

  /**
   * Encodes `value`.

   * @param value the literal string value, or null to encode a null literal.
  * *
   * @return this writer.
   */
  public fun value(value: CharSequence): JsonWriterEx {
    writeDeferredName()
    beforeValue(false)
    string(value)
    return this
  }

  /**
   * Encodes `null`.

   * @return this writer.
   */
  public fun nullValue(): JsonWriterEx {
    if (deferredName != null) {
      if (serializeNulls) {
        writeDeferredName()
      }
      else {
        deferredName = null
        return this // skip the name and the value
      }
    }
    beforeValue(false)
    out.write("null")
    return this
  }

  /**
   * Encodes `value`.

   * @return this writer.
   */
  public fun value(value: Boolean): JsonWriterEx {
    writeDeferredName()
    beforeValue(false)
    out.write(if (value) "true" else "false")
    return this
  }

  public fun value(value: Double): JsonWriterEx {
    if (java.lang.Double.isNaN(value) || java.lang.Double.isInfinite(value)) {
      throw IllegalArgumentException("Numeric values must be finite, but was " + value)
    }
    writeDeferredName()
    beforeValue(false)
    out.write(java.lang.Double.toString(value))
    return this
  }

  /**
   * Encodes `value`.

   * @return this writer.
   */
  public fun value(value: Long): JsonWriterEx {
    writeDeferredName()
    beforeValue(false)
    out.write(java.lang.Long.toString(value))
    return this
  }

  public fun value(value: Number?): JsonWriterEx {
    if (value == null) {
      return nullValue()
    }

    writeDeferredName()
    val string = value.toString()
    if (!lenient && (string == "-Infinity" || string == "Infinity" || string == "NaN")) {
      throw IllegalArgumentException("Numeric values must be finite, but was " + value)
    }
    beforeValue(false)
    out.write(string)
    return this
  }

  private fun checkIsClosed() {
    if (stackSize == 0) {
      throw IllegalStateException("JsonWriter is closed.")
    }
  }

  override fun close() {
    val size = stackSize
    if (size > 1 || size == 1 && stack[size - 1] != NONEMPTY_DOCUMENT) {
      throw IOException("Incomplete document")
    }
    stackSize = 0
  }

  override fun string(value: CharSequence) {
    out.write('"')
    var last = 0
    val length = value.length()
    for (i in 0..length - 1) {
      val c = value[i]
      val replacement: String?
      if (c < 128) {
        replacement = REPLACEMENT_CHARS[c.toInt()]
        if (replacement == null) {
          continue
        }
      }
      else if (c == '\u2028') {
        replacement = "\\u2028"
      }
      else if (c == '\u2029') {
        replacement = "\\u2029"
      }
      else {
        continue
      }
      if (last < i) {
        out.write(value, last, i)
      }
      out.write(replacement)
      last = i + 1
    }
    if (last < length) {
      out.write(value, last)
    }
    out.write('"')
  }

  private fun newline() {
    val space = indent
    if (space == null) {
      return
    }

    out.write("\n")
    val size = stackSize
    for (i in 1..size - 1) {
      out.write(space)
    }
  }

  /**
   * Inserts any necessary separators and whitespace before a name. Also
   * adjusts the stack to expect the name's value.
   */
  private fun beforeName() {
    val context = peek()
    if (context == NONEMPTY_OBJECT) {
      // first in object
      out.write(',')
    }
    else if (context != EMPTY_OBJECT) {
      // not in an object!
      throw IllegalStateException("Nesting problem.")
    }
    newline()
    replaceTop(DANGLING_NAME)
  }

  /**
   * Inserts any necessary separators and whitespace before a literal value,
   * inline array, or inline object. Also adjusts the stack to expect either a
   * closing bracket or another element.

   * @param root true if the value is a new array or object, the two values
  * *     permitted as top-level elements.
   */
  private fun beforeValue(root: Boolean) {
    when (peek()) {
      NONEMPTY_DOCUMENT -> {
        if (!lenient) {
          throw IllegalStateException("JSON must have only one top-level value.")
        }
        emptyDocument(root)
      }

      EMPTY_DOCUMENT -> {
        emptyDocument(root)
      }

      EMPTY_ARRAY -> {
        // first in array
        replaceTop(NONEMPTY_ARRAY)
        newline()
      }

      NONEMPTY_ARRAY -> {
        // another in array
        out.write(',')
        newline()
      }

      DANGLING_NAME -> {
        // value for name
        out.write(separator)
        replaceTop(NONEMPTY_OBJECT)
      }

      else -> throw IllegalStateException("Nesting problem.")
    }
  }

  public fun toByteArray(): ByteArray {
    close()
    return out.toByteArray()
  }

  private fun emptyDocument(root: Boolean) {
    // first in document
    if (!lenient && !root) {
      throw IllegalStateException("JSON must start with an array or an object.")
    }
    replaceTop(NONEMPTY_DOCUMENT)
  }
}