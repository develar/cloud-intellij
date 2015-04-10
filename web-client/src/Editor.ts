"use strict"

import sha1 = require("sha1")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")
import orion = require("orion-api")

import fileSystem = require("FileSystem")

import EditorTopics = service.EditorTopics
import GetResourceResponse = service.GetResourceResponse
import EditorContext = orion.EditorContext
import EditorOptions = orion.EditorOptions

class ResourceMetadata {
  private muteRequests = 0

  public modificationCount = 0

  constructor(public resource: GetResourceResponse) {
  }

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

class Editor implements orion.Validator, orion.LiveEditor, orion.ContentAssist {
  private resourceMetadataPromise: Promise<ResourceMetadata> = null
  private resourceMetadata: ResourceMetadata = null
  private resourceUri: fileSystem.ResourceUri = null
  private editorContext: orion.EditorContext = null

  constructor(private stompClient: stompClient.StompConnector, private fileService: fileSystem.FileService) {
    stompClient.on(EditorTopics.startedResponse, (result: service.EditorStartedResponse) => {
      var resourceMetadata = this.resourceMetadata
      if (resourceMetadata == null || resourceMetadata.modificationCount > 0) {
        return
      }

      var resourceUri = this.resourceUri;
      if (resourceUri == null || resourceUri.path !== result.resource || resourceUri.project !== result.project) {
        return
      }

      resourceMetadata.resource.content = result.content
      resourceMetadata.resource.hash = result.hash
      this.setEditorText(resourceMetadata, result.content)
    })

    stompClient.replyOn(EditorTopics.started, (replyTo: string, correlationId: string, editorStarted: service.EditorStarted) => {
      if (this.resourceMetadata == null) {
        return
      }

      this.editorContext.getText().then((contents) => {
        var resourceMetadata = this.resourceMetadata
        if (resourceMetadata == null) {
          return
        }

        var resourceUri = this.resourceUri;
        if (resourceUri == null || resourceUri.path !== editorStarted.resource || resourceUri.project !== editorStarted.project) {
          return
        }

        var hash = sha1(contents)
        if (hash === editorStarted.hash) {
          return
        }

        this.stompClient.replyToEvent(replyTo, correlationId, {
          project: resourceUri.project,
          resource: resourceUri.path,
          hash: hash,
          content: contents
        })
      })
    })

    this.stompClient.on(EditorTopics.changed, (result: service.EditorChanged) => {
      var resourceMetadata = this.resourceMetadata
      if (resourceMetadata == null || this.editorContext == null) {
        return
      }

      var resourceUri = this.resourceUri
      if (resourceUri == null || resourceUri.path !== result.resource || resourceUri.project !== result.project) {
        return
      }

      this.setEditorText(resourceMetadata, result.addedCharacters, result.offset, result.offset + result.removedCharCount)
    })

    this.stompClient.on(EditorTopics.metadataChanged, (result: service.EditorMetadataChanged) => {
      var resourceMetadata = this.resourceMetadata
      if (resourceMetadata == null || this.editorContext == null) {
        return
      }

      var resourceUri = this.resourceUri
      if (resourceUri == null || resourceUri.path !== result.resource || resourceUri.project !== result.project) {
        return
      }

      this.editorContext.showMarkers(result.markers)
    })
  }

  private setEditorText(resourceMetadata: ResourceMetadata, content: string, start?: number, end?: number) {
    resourceMetadata.modificationCount++
    resourceMetadata._queueMuteRequest()
    var handler = () => {
      resourceMetadata._dequeueMuteRequest()
    }
    this.editorContext.setText(content, start, end).then(handler, handler)
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

  private getResourceData(): Promise<ResourceMetadata> {
    if (this.resourceMetadataPromise != null) {
      return this.resourceMetadataPromise
    }
    else if (this.resourceUri != null) {
      return this.resourceMetadataPromise = this.fileService.getResourceByUri(this.resourceUri)
        .then((data: GetResourceResponse) => {
                this.resourceMetadata = new ResourceMetadata(data)
                return (this.resourceMetadata = new ResourceMetadata(data))
              })
        .cancellable()
    }
    else {
      return Promise.reject("No resource URL")
    }
  }

  onModelChanging(event: orion.ModelChangingEvent): void {
    console.log(JSON.stringify(event))

    if (this.resourceUri == null) {
      console.log("onModelChanging called before startEdit")
      return
    }

    this.getResourceData().then((resourceMetadata: ResourceMetadata) => {
      if (resourceMetadata.canLiveEdit()) {
        var resourceUri = this.resourceUri
        this.stompClient.notify(service.EditorTopics.changed, <service.EditorChanged>{
          project: resourceUri.project,
          resource: resourceUri.path,
          offset: event.start,
          removedCharCount: event.removedCharCount,
          addedCharacters: event.text
        })
      }
    })
  }

  computeContentAssist(editorContext: EditorContext, options: orion.ContentAssistOptions): Promise<Array<any>> {
    return editorContext.getFileMetadata()
      .then((fileMetadata) => {
        var resourceUri = this.fileService.toResourceUri(fileMetadata.location)
        return this.stompClient.request(service.EditorService.contentAssist, {
          project: resourceUri.project,
          path: resourceUri.path,
          offset: options.offset,
          prefix: options.prefix,
          selection: options.selection
        })
      })
      .then((data: any) => {
        var list = data.list
        for (var proposal of list) {
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

  computeProblems(editorContext: EditorContext, options: EditorOptions): Promise<orion.Problems> {
    return this.stompClient.request<orion.Problems>(service.EditorService.problems, this.fileService.toResourceUri(options.title))
  }

  startEdit(editorContext: orion.EditorContext, options: any): Promise<any> {
    var location: string = options == null ? null : (<any>editorContext).title
    var resourceUri = this.fileService.toResourceUri(location)
    if (resourceUri.equals(this.resourceUri)) {
      return
    }

    if (this.resourceMetadataPromise != null) {
      this.resourceMetadataPromise.cancel()
    }

    this.resourceUri = resourceUri
    this.resourceMetadata = null
    this.editorContext = editorContext
    this.resourceMetadataPromise = new Promise((resolve: (result: ResourceMetadata) => void, reject: (error: any) => void) => {
      this.fileService.getResourceByUri(resourceUri).done((result: GetResourceResponse) => {
        if (resourceUri !== this.resourceUri) {
          reject("outdated")
          return
        }

        this.resourceMetadata = new ResourceMetadata(result)
        resolve(this.resourceMetadata)
        this.stompClient.notify(service.EditorTopics.started, {
          project: resourceUri.project,
          resource: resourceUri.path,
          hash: result.hash
        })
      }, reject)
    }).cancellable()
    return null
  }

  endEdit(resource: string): void {
    this.resourceMetadataPromise = null
    this.resourceUri = null
    this.editorContext = null
    this.resourceMetadata = null
  }

  //computeHoverInfo(editorContext: any, ctxt: any) {
  //  return this.getResourceData()
  //    .then((resourceMetadata: any) => {
  //      this.stompClient.request(service.EditorService.javadoc, {
  //        'project': resourceMetadata.project,
  //        'resource': resourceMetadata.resource,
  //        'offset': ctxt.offset,
  //        'length': 0
  //      }).done((data: any) => {
  //        if (data.javadoc != null) {
  //          return {type: 'html', content: data.javadoc.javadoc}
  //        }
  //        else {
  //          return false
  //        }
  //      })
  //    })
  //}

  public applyQuickfix(editorContext: any, context: any) {
    return this.getResourceData()
      .then((resourceMetadata: any) => {
        return this.stompClient.request(service.EditorService.quickfix, {
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource,
          'id': context.annotation.id,
          'offset': context.annotation.start,
          'length': (context.annotation.end - context.annotation.start + 1),
          'apply-fix': true
        })
      })
  }
}

export = Editor