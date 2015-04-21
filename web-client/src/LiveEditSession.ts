"use strict"

import sha1 = require("sha1")
import Promise = require("bluebird")

import {
  EditorTopics,
  EditorStartedResponse,
  EditorStarted,
  DocumentChanged,
  EditorMetadataChanged,
  } from "api/editor"

import {
    StompConnector,
    } from "stompClient"

import {
    ResourceUri,
    } from "ResourceService"

import {
    EditorContext,
    ModelChangingEvent,
    } from "orion-api"

class LiveEditSession {
  private muteRequests = 0
  private modificationCount = 0

  private queueMuteRequest() {
    this.muteRequests++
  }

  private dequeueMuteRequest() {
    this.muteRequests--
  }

  private canLiveEdit() {
    return this.muteRequests === 0
  }

  constructor(private editorContext: EditorContext, public resourceUri: ResourceUri, private stompClient: StompConnector, public callMeOnEnd: (ignored?: any) => void) {
    this.stompClient.notify(EditorTopics.started, resourceUri)
  }

  public startedResponse(result: EditorStartedResponse) {
    if (this.modificationCount > 0) {
      return
    }

    this.setEditorText(result.content)
  }

  public externalStarted(replyTo: string, correlationId: string, event: EditorStarted) {
    this.editorContext.getText().then((contents) => {
      var hash = sha1(contents)
      if (hash === event.hash) {
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

  public externallyChanged(result: DocumentChanged) {
    this.setEditorText(result.newFragment, result.offset, result.offset + result.removedCharCount)
  }

  public metadataChanged(event: EditorMetadataChanged) {
    this.editorContext.showMarkers(event.problems)
  }

  public modelChanging(event: ModelChangingEvent) {
    if (!this.canLiveEdit()) {
      return
    }

    this.modificationCount++

    var resourceUri = this.resourceUri
    this.stompClient.notify(EditorTopics.changed, <DocumentChanged>{
      project: resourceUri.project,
      path: resourceUri.path,
      offset: event.start,
      removedCharCount: event.removedCharCount,
      newFragment: event.text
    })
  }

  private setEditorText(content: string, start?: number, end?: number) {
    this.modificationCount++
    this.queueMuteRequest()
    var handler = () => {
      this.dequeueMuteRequest()
    }
    this.editorContext.setText(content, start, end).then(handler, handler)
  }
}

export = LiveEditSession