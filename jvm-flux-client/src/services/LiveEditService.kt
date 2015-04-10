package org.eclipse.flux.client.services

import com.google.gson.stream.JsonWriter
import org.eclipse.flux.client.EditorTopics
import org.eclipse.flux.client.MessageConnector

/**
 * Broadcast service
 */
public abstract class LiveEditService(private val messageConnector: MessageConnector) {
  init {
    messageConnector.replyOn(EditorTopics.started) {replyTo, correlationId, message ->
      val projectName = message.get("project") as String
      val resourcePath = message.get("resource") as String
      val hash = message.get("hash") as String
      started(replyTo, correlationId, projectName, resourcePath, hash)
    }

    messageConnector.on(EditorTopics.startedResponse) {message ->
      val projectName = message.get("project") as String
      val resourcePath = message.get("resource") as String
      val hash = message.get("hash") as String
      val content = message.get("content") as String
      startedResponse(projectName, resourcePath, hash, content)
    }

    messageConnector.on(EditorTopics.changed) {
      val projectName = it.get("project") as String
      val resourcePath = it.get("resource") as String

      val offset = (it.get("offset") as Double).toInt()
      val removedCharCount = (it.get("removedCharCount") as Double).toInt()
      val addedChars = it.get("addedCharacters") as String?
      changed(projectName, resourcePath, offset, removedCharCount, addedChars)
    }

    messageConnector.replyOn(EditorTopics.allRequested) {replyTo, correlationId, message ->
      [suppress("UNCHECKED_CAST")]
      liveEditors(replyTo, correlationId, message.get("projectRegEx") as String?, message.get("resourceRegEx") as String?, message.get("liveEditUnits") as List<Map<String, Any>>)
    }
  }

  protected abstract fun started(replyTo: String, correlationId: String, projectName: String, resourcePath: String, requestorHash: String)

  protected abstract fun startedResponse(projectName: String, resourcePath: String, savePointHash: String, content: String)

  protected abstract fun changed(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newText: String?)

  protected abstract fun liveEditors(replyTo: String, correlationId: String, projectRegEx: String?, resourceRegEx: String?, liveUnits: List<Map<String, Any>>)

  protected fun notifyChanged(projectName: String, resourcePath: String, offset: Int, removedCharactersCount: Int, newText: CharSequence?) {
    messageConnector.notify(EditorTopics.changed) {
      "project"(projectName)
      "resource"(resourcePath)
      "offset"(offset)
      "removedCharCount"(removedCharactersCount)
      "addedCharacters"(newText ?: "")
    }
  }

  public fun startedMessage(projectName: String, resourcePath: String, hash: String, timestamp: Long) {
    messageConnector.notify(EditorTopics.started) {
      "project"(projectName)
      "resource"(resourcePath)
      "hash"(hash)
      "timestamp"(timestamp)
    }

    started("local", "", projectName, resourcePath, hash)
  }

  protected fun sendStartedResponse(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, content: CharSequence) {
    messageConnector.replyToEvent(replyTo, correlationId) {
      "project"(projectName)
      "resource"(resourcePath)
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
//      "resource" .. path)
//      "savePointHash" .. hash)
//      "savePointTimestamp" .. timestamp)
//      it.endObject()
    }
  }
}