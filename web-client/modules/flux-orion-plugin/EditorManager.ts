import {
  StompConnector
} from "./stompClient"

import {
  FileService
} from "./ResourceService"

import LiveEditSession from "./LiveEditSession"

import EventTarget = require("orion/EventTarget")
import sha1 = require("sha1")
import Promise = require("bluebird")

import {
  EditorTopics,
  EditorStarted,
  EditorStartedResponse,
  DocumentChanged,
  EditorService,
  EditorMetadataChanged,
} from "./api/editor"

import {
  GetResourceResponse,
} from "./api/resource"

import {
  EditorContext,
  EditorOptions,
  Validator,
  ContentAssist,
  LiveEditor,
  Problems,
  ModelChangingEvent,
  ContentAssistOptions,
} from "./orion-api"

export default class EditorManager implements Validator, LiveEditor, ContentAssist {
  public eventTarget = new EventTarget()

  private editSessions: Array<LiveEditSession> = []

  constructor(private stompClient: StompConnector, private fileService: FileService) {
    stompClient.on(EditorTopics.started.response, (result: EditorStartedResponse) => {
      for (let session of this.editSessions) {
        var resourceUri = session.resourceUri
        if (resourceUri.path === result.path || resourceUri.project === result.project) {
          session.startedResponse(result)
          break
        }
      }
    })

    stompClient.replyOn(EditorTopics.started, (replyTo: string, correlationId: string, result: EditorStarted) => {
      for (let session of this.editSessions) {
        var resourceUri = session.resourceUri
        if (resourceUri.path === result.path || resourceUri.project === result.project) {
          session.externalStarted(replyTo, correlationId, result)
          break
        }
      }
    })

    this.stompClient.on(EditorTopics.changed, (result: DocumentChanged) => {
      for (let session of this.editSessions) {
        var resourceUri = session.resourceUri
        if (resourceUri.path === result.path || resourceUri.project === result.project) {
          session.externallyChanged(result)
          break
        }
      }
    })

    this.stompClient.on(EditorTopics.styleReady, (result: any) => {
      result.type = "orion.edit.highlighter.styleReady"
      this.eventTarget.dispatchEvent(result)
    })

    this.stompClient.on(EditorTopics.metadataChanged, (event: EditorMetadataChanged) => {
      for (let session of this.editSessions) {
        var resourceUri = session.resourceUri
        if (resourceUri.path === event.path || resourceUri.project === event.project) {
          session.metadataChanged(event)
          break
        }
      }
    })
  }

  _createSocket() {
    //this.socket.on('getLiveResourcesRequest', function (data) {
    //  self.getResourceData().then(function (resourceMetadata) {
    //    if ((!data.projectRegEx || new RegExp(data.projectRegEx).test(resourceMetadata.project))
    //      && (!data.resourceRegEx || new RegExp(data.resourceRegEx).test(resourceMetadata.resource))) {
    //
    //      var liveEditUnits = {};
    //      liveEditUnits[resourceMetadata.project] = [{
    //        'resource': resourceMetadata.resource,
    //        'savePointHash': resourceMetadata.hash,
    //        'savePointTimestamp': resourceMetadata.timestamp
    //      }];
    //
    //      self.sendMessage('getLiveResourcesResponse', {
    //        'callback_id': data.callback_id,
    //        'requestSenderID': data.requestSenderID,
    //        'username': resourceMetadata.username,
    //        'liveEditUnits': liveEditUnits
    //      });
    //    }
    //  });
    //});
    //
    //this.socket.on('resourceStored', function (data) {
    //  var location = self._rootLocation + data.project + '/' + data.resource;
    //  if (self._resourceUrl === location) {
    //    self._resourceMetadata = createResourceMetadata(data);
    //    self._editorContext.markClean();
    //  }
    //});
    //
    //this.socket.on('serviceRequiredRequest', function (data) {
    //  self.getResourceData().then(function (resourceMetadata) {
    //    if (data.username === resourceMetadata.username
    //      && SERVICE_TO_REGEXP[data.service]
    //      && SERVICE_TO_REGEXP[data.service].test(resourceMetadata.resource)) {
    //
    //      self.sendMessage('serviceRequiredResponse', {
    //        'requestSenderID': data.requestSenderID,
    //        'username': resourceMetadata.username,
    //        'service': data.service
    //      });
    //    }
    //  });
    //});
  }

  onModelChanging(event: ModelChangingEvent): void {
    var resourceUri = this.fileService.toResourceUri(event.file.location)
    for (let session of this.editSessions) {
      if (session.resourceUri.equals(resourceUri)) {
        session.modelChanging(event)
        break
      }
    }
  }

  computeContentAssist(editorContext: EditorContext, options: ContentAssistOptions): Promise<Array<any>> {
    return editorContext.getFileMetadata()
      .then((fileMetadata) => {
        var resourceUri = this.fileService.toResourceUri(fileMetadata.location)
        return this.stompClient.request(EditorService.contentAssist, {
          project: resourceUri.project,
          path: resourceUri.path,
          offset: options.offset,
          prefix: options.prefix,
          selection: options.selection
        })
      })
      .then((data: any) => {
        var list = data.list
        for (let proposal of list) {
          var name: string
          var description: string
          if (proposal.description != null && proposal.description.segments != null) {
            if (proposal.description.segments.length >= 2) {
              name = proposal.description.segments[0].value;
              description = proposal.description.segments[1].value;
            }
            else {
              description = proposal.description.segments[0].value;
            }
          }
          else {
            description = proposal.description;
          }
          if (description == null) {
            description = proposal.proposal;
          }
        }
        return list
      })
  }

  computeProblems(editorContext: EditorContext, options: EditorOptions): Promise<Problems> {
    return this.stompClient.request<Problems>(EditorService.problems, this.fileService.toResourceUri(options.title))
  }

  startEdit(editorContext: EditorContext, options: any): Promise<void> {
    return editorContext.getFileMetadata()
      .then((fileMetadata) => {
        return new Promise<void>((resolve, reject) => {
          this.editSessions.push(new LiveEditSession(editorContext, this.fileService.toResourceUri(fileMetadata.location), this.stompClient, resolve))
        })
      })
  }

  endEdit(location: string): void {
    var uri = this.fileService.toResourceUri(location)
    for (let i = 0, n = this.editSessions.length; i < n; i++) {
      var session = this.editSessions[i];
      if (session.resourceUri.equals(uri)) {
        try {
          this.editSessions.splice(i, 1)
        }
        finally {
          session.callMeOnEnd()
        }
        break
      }
    }
  }

  public applyQuickfix(editorContext: any, context: any) {
    //return this.getResourceData()
    //  .then((resourceMetadata: any) => {
    //    return this.stompClient.request(service.EditorService.quickfix, {
    //      'project': resourceMetadata.project,
    //      'resource': resourceMetadata.resource,
    //      'id': context.annotation.id,
    //      'offset': context.annotation.start,
    //      'length': (context.annotation.end - context.annotation.start + 1),
    //      'apply-fix': true
    //    })
    //  })
  }
}