package org.intellij.flux

class Base64 {
  companion object {
    private val alphabet = charArray('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', //  0 to  7
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', //  8 to 15
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', // 16 to 23
            'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', // 24 to 31
            'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', // 32 to 39
            'o', 'p', 'q', 'r', 's', 't', 'u', 'v', // 40 to 47
            'w', 'x', 'y', 'z', '0', '1', '2', '3', // 48 to 55
            '4', '5', '6', '7', '8', '9', '+', '/')  // 56 to 63

    public fun encode(bytes: ByteArray, length: Int = bytes.size()): CharArray {
      var bits24: Int
      var bits6: Int

      val out = CharArray(((length - 1) / 3 + 1) * 4)

      var outIndex = 0
      var i = 0

      while ((i + 3) <= length) {
        // store the octets
        bits24 = (bytes[i++].toInt() and 255) shl 16
        bits24 = bits24 or ((bytes[i++].toInt() and 255) shl 8)
        bits24 = bits24 or (bytes[i++].toInt() and 255)

        bits6 = (bits24 and 16515072) shr 18
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 258048) shr 12
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 4032) shr 6
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 63)
        out[outIndex++] = alphabet[bits6]
      }

      if (length - i == 2) {
        // store the octets
        bits24 = (bytes[i].toInt() and 255) shl 16
        bits24 = bits24 or ((bytes[i + 1].toInt() and 255) shl 8)

        bits6 = (bits24 and 16515072) shr 18
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 258048) shr 12
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 4032) shr 6
        out[outIndex++] = alphabet[bits6]

        // padding
        out[outIndex] = '='
      }
      else if (length - i == 1) {
        // store the octets
        bits24 = (bytes[i].toInt() and 255) shl 16

        bits6 = (bits24 and 16515072) shr 18
        out[outIndex++] = alphabet[bits6]
        bits6 = (bits24 and 258048) shr 12
        out[outIndex++] = alphabet[bits6]

        // padding
        out[outIndex++] = '='
        out[outIndex] = '='
      }
      return out
    }
  }
}