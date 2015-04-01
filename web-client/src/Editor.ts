"use strict"

import sha1 = require("sha1")
import Deferred = require("orion/Deferred")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")
import orion = require("orion-api")

import fileSystem = require("FileSystem")

import EditorTopics = service.EditorTopics
import GetResourceResponse = service.GetResourceResponse

var SERVICE_TO_REGEXP = {
  "org.eclipse.flux.jdt": new RegExp(".*\\.java|.*\\.class")
};

var callbacksCache = {};

var counter = 1;
function generateCallbackId() {
  return counter++;
}

class ResourceMetadata {
  public liveMarkers: Array<any> = []
  public markers: Array<any> = []

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

class Editor implements orion.Validator, orion.LiveEditor {
  private resourceMetadataPromise: Promise<ResourceMetadata> = null
  private resourceMetadata: ResourceMetadata = null
  private resourceUri: fileSystem.ResourceUri = null
  private editorContext: orion.EditorContext = null

  constructor(private stompClient: stompClient.StompConnector, private fileService: fileSystem.FileService) {
    stompClient.on(EditorTopics.startedResponse, (data: service.EditorStartedResponse) => {
      var resourceMetadata = this.resourceMetadata
      if (resourceMetadata == null || resourceMetadata.modificationCount > 0) {
        return
      }

      var resourceUri = this.resourceUri;
      if (resourceUri == null || resourceUri.path !== data.resource || resourceUri.project !== data.project) {
        return
      }

      resourceMetadata.resource.content = data.content
      resourceMetadata.resource.hash = data.hash

      resourceMetadata._queueMuteRequest();
      this.editorContext.setText(data.content).then(() => {
        resourceMetadata._dequeueMuteRequest();
      }, () => {
        resourceMetadata._dequeueMuteRequest();
      })
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
  }

  _createSocket() {
    //this.socket.on('getMetadataResponse', function (data) {
    //  self._handleMessage(data);
    //});
    //
    //this.socket.on('contentassistresponse', function (data) {
    //  self._handleMessage(data);
    //});
    //
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
    //
    //this.socket.on('liveResourceChanged', function (data) {
    //  self.getResourceData().then(function (resourceMetadata) {
    //    if (data.username === resourceMetadata.username
    //      && data.project === resourceMetadata.project
    //      && data.resource === resourceMetadata.resource
    //      && self._editorContext) {
    //
    //      var text = data.addedCharacters !== undefined ? data.addedCharacters : "";
    //
    //      resourceMetadata._queueMuteRequest();
    //      self._editorContext.setText(text, data.offset, data.offset + data.removedCharCount).then(function () {
    //        resourceMetadata._dequeueMuteRequest();
    //      }, function () {
    //        resourceMetadata._dequeueMuteRequest();
    //      });
    //    }
    //  });
    //});
    //
    //this.socket.on('liveMetadataChanged', function (data) {
    //  self.getResourceData().then(function (resourceMetadata) {
    //    if (resourceMetadata.username === data.username
    //      && resourceMetadata.project === data.project
    //      && resourceMetadata.resource === data.resource
    //      && data.problems !== undefined) {
    //
    //      resourceMetadata.liveMarkers = [];
    //      var i;
    //      for (i = 0; i < data.problems.length; i++) {
    //        //						var lineOffset = editor.getModel().getLineStart(data.problems[i].line - 1);
    //
    //        //						console.log(lineOffset);
    //
    //        resourceMetadata.liveMarkers[i] = {
    //          'id': data.problems[i].id,
    //          'description': data.problems[i].description,
    //          //							'line' : data.problems[i].line,
    //          'severity': data.problems[i].severity,
    //          'start': /*(data.problems[i].start - lineOffset) + 1*/ data.problems[i].start,
    //          'end': /*data.problems[i].end - lineOffset*/ data.problems[i].end
    //        };
    //      }
    //      if (self._editorContext) {
    //        self._editorContext.showMarkers(resourceMetadata.liveMarkers);
    //      }
    //    }
    //    self._handleMessage(data);
    //  });
    //});
    //
    //this.socket.on('javadocresponse', function (data) {
    //  self._handleMessage(data);
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
    if (this.resourceUri == null) {
      console.log("onModelChanging called before startEdit")
      return
    }

    this.getResourceData().then((resourceMetadata: ResourceMetadata) => {
      if (resourceMetadata.canLiveEdit()) {
        var resourceUri = this.resourceUri
        this.stompClient.notify(service.EditorTopics.changed, {
          project: resourceUri.project,
          resource: resourceUri.path,
          offset: event.start,
          removedCharCount: event.removedCharCount,
          addedCharacters: event.text
        })
      }
    })
  }

  computeContentAssist(editorContext: any, options: any) {
    var request = new Deferred();
    this.getResourceData().then((resourceMetadata: any) => {
      this.stompClient.request(service.EditorService.contentAssist, {
        'project': resourceMetadata.project,
        'resource': resourceMetadata.resource,
        'offset': options.offset,
        'prefix': options.prefix,
        'selection': options.selection
      }).done((data: any) => {
        var proposals: any = []
        if (data.proposals) {
          data.proposals.forEach((proposal: any) => {
            var name: string
            var description: string
            if (proposal.description
              && proposal.description.segments
              && (Array.isArray && Array.isArray(proposal.description.segments) || proposal.description.segments instanceof Array)) {

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
            if (!description) {
              description = proposal.proposal;
            }
            if (description) {
              proposals.push({
                'description': description,
                'name': name,
                'overwrite': proposal.replace,
                'positions': proposal.positions,
                'proposal': proposal.proposal,
                'additionalEdits': proposal.additionalEdits,
                'style': "emphasis",
                'escapePosition': proposal.escapePosition
              })
            }
          })
        }
        console.log("Editor content assist: " + JSON.stringify(proposals));
        request.resolve(proposals);
      })
    })
    return request
  }

  computeProblems(editorContext: any, options: any) {
    console.log("Validator (Problems): " + JSON.stringify(options));
    var problemsRequest = new Deferred();
//			this._setEditorInput(options.title, editorContext);

    this.getResourceData().then((resourceMetadata: any) => {
      if (this.resourceUri === options.title) {
        this.stompClient.request(service.EditorService.metadata, {
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource
        }).done((data: any) => {
          resourceMetadata.markers = [];
          for (var i = 0; i < data.metadata.length; i++) {
            resourceMetadata.markers[i] = {
              'id': data.metadata[i].id,
              'description': data.metadata[i].description,
              'severity': data.metadata[i].severity,
              'start': data.metadata[i].start,
              'end': data.metadata[i].end
            };
          }
          problemsRequest.resolve(resourceMetadata.markers)
        })
      }
      else {
        problemsRequest.reject()
      }
    })
    return problemsRequest
  }

  startEdit(editorContext: orion.EditorContext, options: any): Deferred {
    var location: string = options == null ? null : options.title
    if (!this.fileService.isOurResource(location)) {
      return
    }

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

  computeHoverInfo(editorContext: any, ctxt: any) {
    var request = new Deferred();
    this.getResourceData().then((resourceMetadata: any) => {
      this.stompClient.request(service.EditorService.javadoc, {
        'project': resourceMetadata.project,
        'resource': resourceMetadata.resource,
        'offset': ctxt.offset,
        'length': 0
      }).done((data: any) => {
        if (data.javadoc != null) {
          request.resolve({type: 'html', content: data.javadoc.javadoc});
        }
        else {
          request.resolve(false);
        }
      })
    })
    return request
  }

  public applyQuickfix(editorContext: any, context: any) {
    var request = new Deferred();
    this.getResourceData().then((resourceMetadata: any) => {
      this.stompClient.request(service.EditorService.quickfix, {
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource,
          'id': context.annotation.id,
          'offset': context.annotation.start,
          'length': (context.annotation.end - context.annotation.start + 1),
          'apply-fix': true
        })
    })
    return request;
  }
}

export = Editor