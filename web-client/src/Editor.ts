"use strict"

import sha1 = require("sha1")
import Deferred = require("Deferred")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")

import FileSystem = require("FileSystem")

var SERVICE_TO_REGEXP = {
  "org.eclipse.flux.jdt": new RegExp(".*\\.java|.*\\.class")
};

var editSession

var callbacksCache = {};

var counter = 1;
function generateCallbackId() {
  return counter++;
}

function createResourceMetadata(data) {
  var resourceMetadata = {};
  for (var key in data) {
    resourceMetadata[key] = data[key];
  }
  resourceMetadata.liveMarkers = [];
  resourceMetadata.markers = [];
  resourceMetadata._muteRequests = 0;
  resourceMetadata._queueMuteRequest = function () {
    this._muteRequests++;
  };
  resourceMetadata._dequeueMuteRequest = function () {
    this._muteRequests--;
  };
  resourceMetadata._canLiveEdit = function () {
    return this._muteRequests === 0;
  };
  return resourceMetadata;
}

/**
 * Provides operations on files, folders, and projects.
 */
class Editor {
  private _resourceMetadata: any = null
  private _resourceUrl: string = null

  constructor(private stompClient: stompClient.StompConnector, private fileSystem: FileSystem) {
  }

  _createSocket() {
    //this.socket.on('getResourceResponse', function (data) {
    //  self._handleMessage(data);
    //});
    //
    //this.socket.on('getMetadataResponse', function (data) {
    //  self._handleMessage(data);
    //});
    //
    //this.socket.on('contentassistresponse', function (data) {
    //  self._handleMessage(data);
    //});
    //
    //this.socket.on('liveResourceStartedResponse', function (data) {
    //  self._getResourceData().then(function (resourceMetadata) {
    //    if (data.username === resourceMetadata.username &&
    //      data.project === resourceMetadata.project &&
    //      data.resource === resourceMetadata.resource &&
    //      data.callback_id !== undefined &&
    //      resourceMetadata.timestamp === data.savePointTimestamp &&
    //      resourceMetadata.hash === data.savePointHash
    //    ) {
    //      resourceMetadata._queueMuteRequest();
    //      self._editorContext.setText(data.liveContent).then(function () {
    //        resourceMetadata._dequeueMuteRequest();
    //      }, function () {
    //        resourceMetadata._dequeueMuteRequest();
    //      });
    //    }
    //  }, function (err) {
    //    console.log(err);
    //  });
    //});
    //
    //this.socket.on('liveResourceStarted', function (data) {
    //  Deferred.all([self._getResourceData(), self._editorContext.getText()]).then(function (results) {
    //    var resourceMetadata = results[0];
    //    var contents = results[1];
    //    if (resourceMetadata &&
    //      data.username === resourceMetadata.username &&
    //      data.project === resourceMetadata.project &&
    //      data.resource === resourceMetadata.resource &&
    //      data.callback_id !== undefined &&
    //      data.hash === resourceMetadata.hash &&
    //      data.timestamp === resourceMetadata.timestamp
    //    ) {
    //      var livehash = CryptoJS.SHA1(contents).toString(CryptoJS.enc.Hex);
    //
    //      if (livehash !== data.hash) {
    //        self.sendMessage('liveResourceStartedResponse', {
    //          'callback_id': data.callback_id,
    //          'requestSenderID': data.requestSenderID,
    //          'username': data.username,
    //          'project': data.project,
    //          'resource': data.resource,
    //          'savePointTimestamp': resourceMetadata.timestamp,
    //          'savePointHash': resourceMetadata.hash,
    //          'liveContent': contents
    //        });
    //      }
    //    }
    //  });
    //});
    //
    //this.socket.on('getLiveResourcesRequest', function (data) {
    //  self._getResourceData().then(function (resourceMetadata) {
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
    //  self._getResourceData().then(function (resourceMetadata) {
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
    //  self._getResourceData().then(function (resourceMetadata) {
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
    //  self._getResourceData().then(function (resourceMetadata) {
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

  sendMessage(type, message, callbacks) {
    console.log('sendMessage: ', type, message);
//			if (this._connectedToChannel) {
    if (callbacks) {
      message.callback_id = generateCallbackId();
      callbacksCache[message.callback_id] = callbacks;
    }
    else if (!message.callback_id) {
      message.callback_id = 0;
    }
    this.socket.emit(type, message);
    return true;
//			} else {
//				return false;
//			}
  }

  _handleMessage(data) {
    var callbacks = callbacksCache[data.callback_id];
    if (callbacks) {
      if (Array.isArray(callbacks)) {
        var fn = callbacks[0];
        fn.call(this, data);
        callbacks.shift();
        if (callbacks.length === 0) {
          delete callbacksCache[data.callback_id];
        }
        return true;
      }
      else if (callbacks.call) {
        callbacks.call(this, data);
        delete callbacksCache[data.callback_id];
        return true;
      }
    }
    return false;
  }

  _getResourceData(): Promise {
    if (this._resourceMetadata != null) {
      return Promise.resolve(this._resourceMetadata);
    }
    else if (this._resourceUrl != null) {
      return this.fileSystem.getResource(this._resourceUrl).then((data: any) => {
        this._resourceMetadata = createResourceMetadata(data)
        return this._resourceMetadata
      })
    }
    else {
      return Promise.reject("No resource URL")
    }
  }

  _setEditorInput(resourceUrl, editorContext) {
    var self = this;
    if (this._resourceUrl !== resourceUrl) {
      this._resourceUrl = null;
      this._editorContext = null;
      this._resourceMetadata = null;
      if (editSession) {
        editSession.resolve();
      }
      if (this.fileSystem.isFluxResource(resourceUrl)) {
        this._resourceUrl = resourceUrl;
        editSession = new Deferred();
        this._editorContext = editorContext;

        this._getResourceData().then(function (resourceMetadata) {
          self.sendMessage('liveResourceStarted', {
            'callback_id': 0,
            'username': resourceMetadata.username,
            'project': resourceMetadata.project,
            'resource': resourceMetadata.resource,
            'hash': resourceMetadata.hash,
            'timestamp': resourceMetadata.timestamp
          });
        });
      }
    }
    return editSession;
  }

  onModelChanging(evt) {
    console.log("Editor changing: " + JSON.stringify(evt));
    var self = this;
    this._getResourceData().then(function (resourceMetadata) {
      if (resourceMetadata._canLiveEdit()) {
        var changeData = {
          'username': resourceMetadata.username,
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource,
          'offset': evt.start,
          'removedCharCount': evt.removedCharCount,
          'addedCharacters': evt.text
        };

        self.sendMessage('liveResourceChanged', changeData);
      }
    });
  }

  computeContentAssist(editorContext, options) {
    var request = new Deferred();
    var self = this;
    this._getResourceData().then(function (resourceMetadata) {
      self.sendMessage("contentassistrequest", {
        'username': resourceMetadata.username,
        'project': resourceMetadata.project,
        'resource': resourceMetadata.resource,
        'offset': options.offset,
        'prefix': options.prefix,
        'selection': options.selection
      }, function (data) {
        var proposals = [];
        if (data.proposals) {
          data.proposals.forEach(function (proposal) {
            var name;
            var description;
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
              });
            }
          });
        }
        console.log("Editor content assist: " + JSON.stringify(proposals));
        request.resolve(proposals);
      });
    });
    return request;
  }

  computeProblems(editorContext, options) {
    console.log("Validator (Problems): " + JSON.stringify(options));
    var self = this;
    var problemsRequest = new Deferred();
//			this._setEditorInput(options.title, editorContext);

    this._getResourceData().then(function (resourceMetadata) {
      if (self._resourceUrl === options.title) {
        self.sendMessage("getMetadataRequest", {
          'username': resourceMetadata.username,
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource
        }, function (data) {
          if (data.username === resourceMetadata.username
            && data.project === resourceMetadata.project
            && data.resource === resourceMetadata.resource) {

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
          }
          problemsRequest.resolve(resourceMetadata.markers);
        });
      }
      else {
        problemsRequest.reject();
      }
    });

    return problemsRequest;
  }

  startEdit(editorContext, options) {
    this.jdtInitializer = this._initializeJDT(editorContext);
    var url = options ? options.title : null;
    return this._setEditorInput(url, editorContext);
  }

  endEdit(resourceUrl) {
    if (this.jdtInitializer) {
      this.jdtInitializer.dispose();
      delete this.jdtInitializer;
    }
    this._setEditorInput(null, null);
  }

  computeHoverInfo(editorContext, ctxt) {
    var self = this;
    var request = new Deferred();
    this._getResourceData().then(function (resourceMetadata) {
      self.sendMessage("javadocrequest", {
          'username': self.user,
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource,
          'offset': ctxt.offset,
          'length': 0
        }, function (data) {
                         if (self.user === data.username && resourceMetadata.project === data.project
                           && data.javadoc !== undefined) {
                           request.resolve({type: 'html', content: data.javadoc.javadoc});
                         }
                         else {
                           request.resolve(false);
                         }
                       }
      );
    });
    return request;
  }

  applyQuickfix(editorContext, ctxt) {
    var self = this;
    var request = new Deferred();
    this._getResourceData().then(function (resourceMetadata) {
      self.sendMessage("quickfixrequest", {
          'project': resourceMetadata.project,
          'resource': resourceMetadata.resource,
          'id': ctxt.annotation.id,
          'offset': ctxt.annotation.start,
          'length': (ctxt.annotation.end - ctxt.annotation.start + 1),
          'apply-fix': true
        }, function (data) {
                         console.log(data);
                       }
      );
    });
    return request;
  }
}

export = FileSystem