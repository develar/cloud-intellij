package io.netty.buffer

import java.io.UTFDataFormatException
import java.nio.CharBuffer

public fun readChars(buffer: ByteBuf): CharSequence {
  return MyCharArrayCharSequence(readIntoCharBuffer(buffer, buffer.readableBytes()))
}

public fun readChars(buffer: ByteBuf, byteCount: Int): CharSequence {
  return MyCharArrayCharSequence(readIntoCharBuffer(buffer, byteCount))
}

public fun readIntoCharBuffer(buffer: ByteBuf, byteCount: Int, charBuffer: CharBuffer = CharBuffer.allocate(byteCount)): CharBuffer {
  readUtf8(buffer, byteCount, charBuffer)
  return charBuffer
}

// we must return string on subSequence() - JsonReaderEx will call toString in any case
public class MyCharArrayCharSequence(charBuffer: CharBuffer) : CharArrayCharSequence(charBuffer.array(), charBuffer.arrayOffset(), charBuffer.position()) {
  override fun subSequence(start: Int, end: Int): CharSequence {
    return if (start == 0 && end == length()) this else String(myChars, myStart + start, end - start)
  }
}

public open class CharArrayCharSequence(protected val myChars: CharArray, protected val myStart: Int, protected val myEnd: Int) : CharSequence {
  public constructor(vararg chars: Char) : this(chars, 0, chars.size()) {
  }

  init {
    if (myStart < 0 || myEnd > myChars.size() || myStart > myEnd) {
      throw IndexOutOfBoundsException("chars.length:" + myChars.size() + ", start:" + myStart + ", end:" + myEnd)
    }
  }

  override fun length(): Int {
    return myEnd - myStart
  }

  override fun charAt(index: Int): Char {
    return myChars[index + myStart]
  }

  override fun subSequence(start: Int, end: Int): CharSequence {
    return if (start == 0 && end == length()) this else CharArrayCharSequence(myChars, myStart + start, myStart + end)
  }

  override fun toString(): String {
    return String(myChars, myStart, myEnd - myStart) //TODO StringFactory
  }

  override fun equals(other: Any?): Boolean {
    if (this == other) {
      return true
    }
    if (other is CharSequence) {
      val n = myEnd - myStart
      if (n == other.length()) {
        for (i in 0..n - 1) {
          if (myChars[myStart + i] != other.charAt(i)) {
            return false
          }
        }
        return true
      }
    }
    return false
  }

  public fun readCharsTo(start: Int, cbuf: CharArray, off: Int, len: Int): Int {
    val readChars = Math.min(len, length() - start)
    if (readChars <= 0) return -1

    System.arraycopy(myChars, myStart + start, cbuf, off, readChars)
    return readChars
  }
}

private fun getBuf(buffer: ByteBuf): AbstractByteBuf {
  if (buffer is AbstractByteBuf) {
    return buffer
  }
  else {
    return (buffer as WrappedByteBuf).buf as AbstractByteBuf
  }
}

public fun readUtf8(buf: ByteBuf, byteCount: Int, charBuffer: CharBuffer) {
  val buffer = getBuf(buf)
  val readerIndex = buf.readerIndex()

  val c: Int
  val char2: Int
  val char3: Int
  var count = 0

  var byteIndex = readerIndex
  var charIndex = charBuffer.position()
  val chars = charBuffer.array()
  while (count < byteCount) {
    c = buffer._getByte(byteIndex++).toInt() and 255
    if (c > 127) {
      break
    }

    count++
    chars[charIndex++] = c.toChar()
  }

  // byteIndex incremented before check "c > 127", so, we must reset it
  byteIndex = readerIndex + count
  while (count < byteCount) {
    c = buffer._getByte(byteIndex++).toInt() and 255
    when (c shr 4) {
      0, 1, 2, 3, 4, 5, 6, 7 -> {
        // 0xxxxxxx
        count++
        chars[charIndex++] = c.toChar()
      }

      12, 13 -> {
        count += 2
        if (count > byteCount) {
          throw UTFDataFormatException("malformed input: partial character at end")
        }
        char2 = buffer._getByte(byteIndex++).toInt()
        if ((char2 and 192) != 128) {
          throw UTFDataFormatException("malformed input around byte " + count)
        }
        chars[charIndex++] = (((c and 31) shl 6) or (char2 and 63)).toChar()
      }

      14 -> {
        count += 3
        if (count > byteCount) {
          throw UTFDataFormatException("malformed input: partial character at end")
        }
        char2 = buffer._getByte(byteIndex++).toInt()
        char3 = buffer._getByte(byteIndex++).toInt()
        if (((char2 and 192) != 128) || ((char3 and 192) != 128)) {
          throw UTFDataFormatException("malformed input around byte " + (count - 1))
        }
        chars[charIndex++] = (((c and 15) shl 12) or ((char2 and 63) shl 6) or ((char3 and 63))).toChar()
      }

      else -> throw UTFDataFormatException("malformed input around byte " + count)
    }
  }

  if (buf == buffer) {
    buffer.readerIndex = readerIndex + byteCount
  }
  else {
    buf.readerIndex(readerIndex + byteCount)
  }
  charBuffer.position(charIndex)
}