package org.jetbrains.json

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken

public fun ByteArray.jsonReader(): JsonReader = JsonReader(inputStream.reader())

public inline fun JsonReader.map(f: JsonReader.() -> Unit) {
  beginObject()
  while (hasNext()) {
    f()
  }
  endObject()
}

public fun JsonReader.nextNullableString(): String? {
  if (peek() == JsonToken.NULL) {
    nextNull()
    return null
  }
  else {
    return nextString()
  }
}

