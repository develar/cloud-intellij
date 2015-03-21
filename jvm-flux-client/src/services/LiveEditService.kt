package org.eclipse.flux.client.services

import com.google.gson.stream.JsonWriter
import org.eclipse.flux.client.LiveEditTopics
import org.eclipse.flux.client.MessageConnector

/**
 * Broadcast service
 */
public abstract class LiveEditService(private val messageConnector: MessageConnector) {
  init {
    messageConnector.replyOn(LiveEditTopics.liveResourceStarted) {replyTo, correlationId, message ->
      val projectName = message.get("project") as String
      val resourcePath = message.get("resource") as String
      val hash = message.get("hash") as String
      val timestamp = message.get("timestamp") as Long
      liveEditingStarted(replyTo, correlationId, projectName, resourcePath, hash, timestamp)
    }

    messageConnector.on(LiveEditTopics.liveResourceStartedResponse) {message ->
      val projectName = message.get("project") as String
      val resourcePath = message.get("resource") as String
      val savePointHash = message.get("savePointHash") as String
      val savePointTimestamp = message.get("savePointTimestamp") as Long
      val content = message.get("liveContent") as String
      liveEditingStartedResponse(projectName, resourcePath, savePointHash, savePointTimestamp, content)
    }

    messageConnector.on(LiveEditTopics.liveResourceChanged) {
      modelChanged(it)
    }

    messageConnector.replyOn(LiveEditTopics.liveResourcesRequested) {replyTo, correlationId, message ->
      [suppress("UNCHECKED_CAST")]
      liveEditors(replyTo, correlationId, message.get("projectRegEx") as String?, message.get("resourceRegEx") as String?, message.get("liveEditUnits") as List<Map<String, Any>>)
    }
  }

  protected abstract fun liveEditingStarted(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, timestamp: Long)

  protected abstract fun liveEditingStartedResponse(projectName: String, resourcePath: String, savePointHash: String, savePointTimestamp: Long, content: String)

  protected abstract fun liveEditingEvent(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newText: String?)

  protected abstract fun liveEditors(replyTo: String, correlationId: String, projectRegEx: String?, resourceRegEx: String?, liveUnits: List<Map<String, Any>>)

  private fun modelChanged(message: Map<String, Any>) {
    val projectName = message.get("project") as String
    val resourcePath = message.get("resource") as String

    val offset = message.get("offset") as Int
    val removedCharCount = message.get("removedCharCount") as Int
    val addedChars = message.get("addedCharacters") as String? ?: ""

    liveEditingEvent(projectName, resourcePath, offset, removedCharCount, addedChars)
  }

  public fun sendModelChangedMessage(projectName: String, resourcePath: String, offset: Int, removedCharactersCount: Int, newText: String?) {
    messageConnector.notify(LiveEditTopics.liveResourceChanged) {
      it.name("project").value(projectName)
      it.name("resource").value(resourcePath)
      it.name("offset").value(offset)
      it.name("removedCharCount").value(removedCharactersCount)
      it.name("addedCharacters").value(newText ?: "")
    }
    liveEditingEvent(projectName, resourcePath, offset, removedCharactersCount, newText)
  }

  public fun sendLiveEditStartedMessage(projectName: String, resourcePath: String, hash: String, timestamp: Long) {
    messageConnector.notify(LiveEditTopics.liveResourceStarted) {
      it.name("project").value(projectName)
      it.name("callback_id").value(0)
      it.name("resource").value(resourcePath)
      it.name("hash").value(hash)
      it.name("timestamp").value(timestamp)
    }

    liveEditingStarted("local", "", projectName, resourcePath, hash, timestamp)
  }

  protected fun sendLiveEditStartedResponse(replyTo: String, correlationId: String, projectName: String, resourcePath: String, savePointHash: String, savePointTimestamp: Long, content: String) {
    messageConnector.replyToEvent(replyTo, correlationId) {
      it.name("project").value(projectName)
      it.name("resource").value(resourcePath)
      it.name("savePointTimestamp").value(savePointTimestamp)
      it.name("savePointHash").value(savePointHash)
      it.name("liveContent").value(content)
    }

    liveEditingStartedResponse(projectName, resourcePath, savePointHash, savePointTimestamp, content)
  }

  protected fun sendLiveResourcesResponse(replyTo: String, correlationId: String, liveUnits: Map<String, List<ResourceData>>) {
    // don't send anything if there is nothing to send
    if (liveUnits.isEmpty()) {
      return
    }

    messageConnector.replyToEvent(replyTo, correlationId) {
      it.name("liveEditUnits").beginObject()
      for (entry in liveUnits.entrySet()) {
        it.name(entry.getKey())
        for (data in entry.getValue()) {
          data.write(it)
        }
      }
      it.endObject()
    }
  }

  protected class ResourceData(private val path: String, private val hash: String, private val timestamp: Long) {
    fun write(it: JsonWriter) {
      it.beginObject();
      it.name("resource").value(path)
      it.name("savePointHash").value(hash)
      it.name("savePointTimestamp").value(timestamp)
      it.endObject()
    }
  }
}