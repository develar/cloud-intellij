package org.eclipse.flux.client

trait EditorTopics {
  companion object {
    val started = TopicWithResponse("editor.started")

    /**
     * Delta information about a live change to a resource
     */
    val changed = TopicWithResponse("editor.changed")

//    val allRequested = Topic("editor.allRequested", true)
//    val allRequestedResponse = Topic(allRequested.responseName!!)

    val metadataChanged = Topic("editor.metadataChanged")
  }
}

trait ResourceTopics {
  companion object {
    val created = Topic("resource.created")
    val changed = Topic("resource.changed")
    val deleted = Topic("resource.deleted")
    val saved = Topic("resource.saved")
  }
}

trait ProjectTopics {
  companion object {
    val connected = Topic("project.connected")
    val disconnected = Topic("project.disconnected")
  }
}