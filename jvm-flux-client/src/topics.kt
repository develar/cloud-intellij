package org.eclipse.flux.client

enum class LiveEditTopics : Topic {
  override val name: String
    get() = name()

  /**
   * A resource is going to be edited
   */
  liveResourceStarted
  liveResourceChanged

  liveResourceStartedResponse

  /**
   * All participants asking for the resources that are being edited at the moment
   */
  liveResourcesRequested {
    override val responseName: String?
      get() = liveResources.name
  }
  // broadcast request (liveResourcesRequested -> n liveResources direct messages)
  liveResources

  liveMetadataChanged
}

enum class ResourceTopics : Topic {
  override val name: String
    get() = name()

  resourceChanged
  resourceCreated
  resourceDeleted

  resourceStored
}

enum class ProjectTopics : Topic {
  override val name: String
    get() = name()

  projectConnected
  projectDisconnected
}