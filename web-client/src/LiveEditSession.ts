"use strict"

import sha1 = require("sha1")
import stompClient = require("stompClient")
import Promise = require("bluebird")
import orion = require("orion-api")
import fileSystem = require("FileSystem")

import {
  EditorTopics,
  EditorStartedResponse,
  EditorStarted,
  DocumentChanged,
  } from "api/editor"

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

  constructor(private editorContext: orion.EditorContext, public resourceUri: fileSystem.ResourceUri, private stompClient: stompClient.StompConnector, public callMeOnEnd: (ignored?: any) => void) {
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

  public modelChanging(event: orion.ModelChangingEvent) {
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