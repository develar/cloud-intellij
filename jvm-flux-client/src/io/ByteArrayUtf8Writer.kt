package org.jetbrains.json

import java.util.Arrays

public class ByteArrayUtf8Writer()  {
  private var buffer = ByteArray(32)
  private var count = 0
  private var mark = 0

  public fun size(): Int = count

  public fun isEmpty(): Boolean = count == 0

  private fun ensureCapacity(minCapacity: Int) {
    val newCount = count + minCapacity
    if (newCount > buffer.size()) {
      buffer = Arrays.copyOf(buffer, Math.max(buffer.size() shl 1, newCount))
    }
  }

  public fun mark() {
    mark = count
  }

  public fun reset() {
    count = mark
  }

  public fun write(c: Char) {
    write(c.toInt())
  }

  public fun write(c: Int) {
    if (c < 128) {
      ensureCapacity(1)
      buffer[count++] = c.toByte()
    }
    else if (c > 2047) {
      ensureCapacity(3)
      buffer[count++] = (224 or ((c shr 12) and 15)).toByte()
      buffer[count++] = (128 or ((c shr 6) and 63)).toByte()
      buffer[count++] = (128 or ((c shr 0) and 63)).toByte()
    }
    else {
      ensureCapacity(2)
      buffer[count++] = (192 or ((c shr 6) and 31)).toByte()
      buffer[count++] = (128 or ((c shr 0) and 63)).toByte()
    }
  }

  public fun write(string: CharSequence, start: Int = 0, end: Int = string.length()) {
    var writerIndex = count
    ensureCapacity(end - start)

    var i = start
    while (i < end) {
      val c = string[i].toInt()
      if (!(c >= 1 && c <= 127)) {
        break
      }

      buffer[writerIndex++] = c.toByte()
      i++
    }

    if (i < end) {
      ensureCapacity((end - i) * 3)
      do {
        val c = string[i].toInt()
        if (c >= 1 && c <= 127) {
          buffer[writerIndex++] = c.toByte()
        }
        else if (c > 2047) {
          buffer[writerIndex++] = (224 or ((c shr 12) and 15)).toByte()
          buffer[writerIndex++] = (128 or ((c shr 6) and 63)).toByte()
          buffer[writerIndex++] = (128 or ((c shr 0) and 63)).toByte()
        }
        else {
          buffer[writerIndex++] = (192 or ((c shr 6) and 31)).toByte()
          buffer[writerIndex++] = (128 or ((c shr 0) and 63)).toByte()
        }
      }
      while (++i < end)
    }
    count = writerIndex
  }

  public fun toByteArray(): ByteArray {
    if (buffer.size() == count) {
      return buffer
    }
    else {
      return Arrays.copyOf(buffer, count)
    }
  }
}