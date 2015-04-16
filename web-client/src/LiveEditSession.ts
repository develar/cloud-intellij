"use strict"

import sha1 = require("sha1")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")
import orion = require("orion-api")
import fileSystem = require("FileSystem")

class ResourceMetadata {
  private muteRequests = 0
  public modificationCount = 0

  public _queueMuteRequest() {
    this.muteRequests++
  }

  public _dequeueMuteRequest() {
    this.muteRequests--
  }

  public canLiveEdit() {
    return this.muteRequests === 0
  }
}

class LiveEditSession {
  private resourceMetadata: ResourceMetadata = null

  constructor(private editorContext: orion.EditorContext, public resourceUri: fileSystem.ResourceUri, private stompClient: stompClient.StompConnector, public callMeOnEnd: (ignored?: any) => void) {
    this.stompClient.notify(service.EditorTopics.started, resourceUri)
  }

  public startedResponse(result: service.EditorStartedResponse) {
    if (this.resourceMetadata == null) {
      this.resourceMetadata = new ResourceMetadata()
    }

    var resourceMetadata = this.resourceMetadata
    if (resourceMetadata.modificationCount > 0) {
      return
    }

    this.setEditorText(resourceMetadata, result.content)
  }

  public started(replyTo: string, correlationId: string, result: service.EditorStarted) {
    this.editorContext.getText().then((contents) => {
      var resourceMetadata = this.resourceMetadata
      if (resourceMetadata == null) {
        return
      }

      var hash = sha1(contents)
      if (hash === result.hash) {
        return
      }

      this.stompClient.replyToEvent(replyTo, correlationId, {
        project: this.resourceUri.project,
        resource: this.resourceUri.path,
        hash: hash,
        content: contents
      })
    })
  }

  public changed(result: service.DocumentChanged) {
    var resourceMetadata = this.resourceMetadata
    if (resourceMetadata == null) {
      return
    }

    this.setEditorText(resourceMetadata, result.newFragment, result.offset, result.offset + result.removedCharCount)
  }

  public  modelChanging(event: orion.ModelChangingEvent) {
    if (this.resourceMetadata != null && this.resourceMetadata.canLiveEdit()) {
      var resourceUri = this.resourceUri
      this.stompClient.notify(service.EditorTopics.changed, <service.DocumentChanged>{
        project: resourceUri.project,
        path: resourceUri.path,
        offset: event.start,
        removedCharCount: event.removedCharCount,
        newFragment: event.text
      })
    }
  }

  private setEditorText(resourceMetadata: ResourceMetadata, content: string, start?: number, end?: number) {
    resourceMetadata.modificationCount++
    resourceMetadata._queueMuteRequest()
    var handler = () => {
      resourceMetadata._dequeueMuteRequest()
    }
    this.editorContext.setText(content, start, end).then(handler, handler)
  }
}

export = LiveEditSession