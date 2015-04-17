package org.intellij.flux

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