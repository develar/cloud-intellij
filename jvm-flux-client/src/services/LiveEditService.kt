package org.jetbrains.flux

import org.jetbrains.json.map
import org.jetbrains.json.nextNullableString

/**
 * Broadcast service
 */
public abstract class LiveEditService(protected val messageConnector: MessageConnector) {
  init {
    messageConnector.replyOn(EditorTopics.started) {message, replyTo, correlationId ->
      var project: String? = null
      var path: String? = null
      var hash: String? = null
      message.map {
        when (nextName()) {
          "project" -> project = nextString()
          "path" -> path = nextString()
          "hash" -> hash = nextNullableString()
          else -> skipValue()
        }
      }
      started(project!!, path!!, hash, replyTo, correlationId)
    }

    messageConnector.on(EditorTopics.started.response) {
      var project: String? = null
      var path: String? = null
      var hash: String? = null
      var content: String? = null
      it.map {
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

    messageConnector.replyOn(EditorTopics.changed) {message, replyTo, correlationId ->
      var project: String? = null
      var path: String? = null
      var offset = 0
      var removedCharCount = 0
      var newFragment: String? = null
      message.map {
        when (nextName()) {
          "project" -> project = nextString()
          "path" -> path = nextString()
          "offset" -> offset = nextInt()
          "removedCharCount" -> removedCharCount = nextInt()
          "newFragment" -> newFragment = nextNullableString()
          else -> skipValue()
        }
      }

      changed(project!!, path!!, offset, removedCharCount, newFragment, replyTo, correlationId)
    }
  }

  protected abstract fun started(projectName: String, resourcePath: String, requestorHash: String?, replyTo: String, correlationId: String)

  protected abstract fun startedResponse(projectName: String, resourcePath: String, savePointHash: String, content: String)

  protected abstract fun changed(projectName: String, resourcePath: String, offset: Int, removeCount: Int, newFragment: String?, replyTo: String, correlationId: String)

  protected fun notifyChanged(projectName: String, resourcePath: String, offset: Int, removedCharactersCount: Int, newText: CharSequence?) {
    messageConnector.notify(EditorTopics.changed) {
      "project"(projectName)
      "path"(resourcePath)

      "offset"(offset)
      "removedCharCount"(removedCharactersCount)
      "newFragment"(newText ?: "")
    }
  }

  protected fun sendStartedResponse(replyTo: String, correlationId: String, projectName: String, resourcePath: String, hash: String, content: CharSequence) {
    messageConnector.replyToEvent(replyTo, correlationId) {
      "project"(projectName)
      "path"(resourcePath)
      "hash"(hash)
      "content"(content)
    }
  }
}