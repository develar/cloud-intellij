package org.eclipse.flux.client.services

import com.google.gson.stream.JsonWriter
import org.eclipse.flux.client.EditorTopics
import org.eclipse.flux.client.MessageConnector
import org.jetbrains.json.jsonReader
import org.jetbrains.json.map
import org.jetbrains.json.nextNullableString

/**
 * Broadcast service
 */
public abstract class LiveEditService(protected val messageConnector: MessageConnector) {
  init {
    messageConnector.replyOn(EditorTopics.started) {replyTo, correlationId, message ->
      var project: String? = null
      var path: String? = null
      var hash: String? = null
      message.jsonReader().map {
        when (nextName()) {
          "project" -> project = nextString()
          "path" -> path = nextString()
          "hash" -> hash = nextNullableString()
          else -> skipValue()
        }
      }
      started(replyTo, correlationId, project!!, path!!, hash)
    }

    messageConnector.on(EditorTopics.startedResponse) {
      var project: String? = null
      var path: String? = null
      var hash: String? = null
      var content: String? = null
      it.jsonReader().map {
        when (nextName()) {
          "project" -> project = nextString()
          "path" -> path = nextString()
          "hash" -> hash = nextString()
          "content" -> content = nextString()
          else -> skipValue()
        }
      }

      startedResponse(project!!, path!!, hash!!, content!!)
    }

    messageConnector.on(EditorTopics.changed) {
      var project: String? = null
      var path: String? = null
      var offset = 0
      var removedCharCount = 0
      var newFragment: String? = null
      it.jsonReader().map {
        when (nextName()) {
          "project" -> project = nextString()
          "path" -> path = nextString()
          "offset" -> offset = nextInt()
          "removedCharCount" -> removedCharCount = nextInt()
          "newFragment" -> newFragment = nextNullableString()
          else -> skipValue()
        }
      }

      changed(project!!, path!!, offset, removedCharCount, newFragment)
    }

//    messageConnector.replyOn(EditorTopics.allRequested) {replyTo, correlationId, message ->
//      [suppress("UNCHECKED_CAST")]
//      liveEditors(replyTo, correlationId, message.get("projectRegEx") as String?, message.get("resourceRegEx") as String?, message.get("liveEditUnits") as List<Map<String, Any>>)
//    }
  }

  protected abstract fun started(replyTo: String, correlationId: String, projectName: String, resourcePath: String, requestorHash: String?)

  protected abstract fun startedResponse(projectName: String, resourcePath: String, savePointHash: String, content: String)

  protected abstract fun changed(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newFragment: String?)

  protected abstract fun liveEditors(replyTo: String, correlationId: String, projectRegEx: String?, resourceRegEx: String?, liveUnits: List<Map<String, Any>>)

  protected fun notifyChanged(projectName: String, resourcePath: String, offset: Int, removedCharactersCount: Int, newText: CharSequence?) {
    messageConnector.notify(EditorTopics.changed) {
      "project"(projectName)
      "path"(resourcePath)

      "offset"(offset)
      "removedCharCount"(removedCharactersCount)
      "newFragment"(newText ?: "")
    }
  }

  public fun startedMessage(projectName: String, resourcePath: String, hash: String, timestamp: Long) {
    messageConnector.notify(EditorTopics.started) {
      "project"(projectName)
      "path"(resourcePath)
      "hash"(hash)
      "timestamp"(timestamp)
    }

    started("local", "", projectName, resourcePath, hash)
  }

  protected fun sendStartedResponse(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, content: CharSequence) {
    messageConnector.replyToEvent(replyTo, correlationId) {
      "project"(projectName)
      "path"(resourcePath)
      "hash"(hash)
      "content"(content)
    }
  }

  protected fun sendLiveResourcesResponse(replyTo: String, correlationId: String, liveUnits: Map<String, List<ResourceData>>) {
    // don't send anything if there is nothing to send
    if (liveUnits.isEmpty()) {
      return
    }

//    messageConnector.replyToEvent(replyTo, correlationId) {
//      "liveEditUnits").beginObject()
//      for (entry in liveUnits.entrySet()) {
//        entry.getKey())
//        for (data in entry.getValue()) {
//          data.write(it)
//        }
//      }
//      it.endObject()
//    }
  }

  protected class ResourceData(private val path: String, private val hash: String, private val timestamp: Long) {
    fun write(it: JsonWriter) {
//      it.beginObject();
//      "path" .. path)
//      "savePointHash" .. hash)
//      "savePointTimestamp" .. timestamp)
//      it.endObject()
    }
  }
}