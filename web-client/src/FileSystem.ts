"use strict"

import sha1 = require("sha1")
import Deferred = require("orion/Deferred")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")
import orion = require("orion-api")

import ProjectService = service.ProjectService
import ResourceService = service.ResourceService
import ResourceTopics = service.ResourceTopics
import ProjectTopics = service.ProjectTopics
import Projects = service.Projects

/**
 * An implementation of the file service that understands the Orion
 * server file API. This implementation is suitable for invocation by a remote plugin.
 */
function assignAncestry(parents: { [key: string]: any; }, childrenDepthMap: { [key: number]: Array<Entry>; }, depth: number): void {
  if (!childrenDepthMap[depth]) {
    return
  }

  var newParents: { [key: string]: Entry; } = {};
  for (var i in childrenDepthMap[depth]) {
    var child = childrenDepthMap[depth][i]
    if (depth > 0) {
      var parentLocation = child.Location.substr(0, child.Location.lastIndexOf('/'))
      if (parents[parentLocation]) {
        var parent = parents[parentLocation]
        if (!parent.Children) {
          parent.Children = []
        }
        parent.Children.push(child);
        if (!parent._childrenCache) {
          parent._childrenCache = {};
        }
        parent._childrenCache[child.Name] = child;
        if (child.Parents == null) {
          child.Parents = [];
        }
        child.Parents.push(parent);
        if (!parent.ChildrenLocation) {
          parent.ChildrenLocation = parent.Location + '/';
        }
      }
      else {
        if (parentLocation) {
          throw new Error("Parent is missing!");
        }
      }
    }
    newParents[child.Location] = child;
  }
  assignAncestry(newParents, childrenDepthMap, depth + 1);
}

function promiseToDeferred<T>(promise: Promise<T>): Deferred {
  var deferred = new Deferred()
  promise.done((result: T) => {
    deferred.resolve(result)
  }, (error: any) => {
    deferred.reject(error)
  })
  return deferred
}

export class ResourceUri {
  constructor(public project: string, public path?: string) {
  }

  public equals(other?: ResourceUri): boolean {
    return other != null && other.project === this.project && other.path === this.path
  }
}

export class FileService implements orion.FileClient {
  private workspace: any
  private saves: { [key: string]: Saved; } = {};
  
  constructor(private stompClient: stompClient.StompConnector, private rootLocation: string) {}

  public toResourceUri(location: string): ResourceUri {
    if (location == null) {
      location = "/"
    }
    else {
      location = location.replace(this.rootLocation, "")
    }
    var indexOfDelimiter = location.indexOf('/')
    var project = indexOfDelimiter < 0 ? location : location.substr(0, indexOfDelimiter)
    return new ResourceUri(project, indexOfDelimiter < 0 ? null : location.substr(indexOfDelimiter + 1))
  }

  public isOurResource(location?: string) {
    return location != null && location.indexOf(this.rootLocation) === 0
  }

  fetchChildren(location: string): Deferred {
    if (location.charAt(location.length - 1) === '/') {
      location = location.substr(0, location.length - 1)
    }
    return promiseToDeferred(this.findFromLocation(location).then(function (parent: any) {
      return parent && parent.Children ? parent.Children : [];
    }))
  }

  private createOrionProject(data: Projects.GetResponse, projectName: string): Entry //noinspection UnterminatedStatementJS
  {
    var result = new Entry(projectName, projectName + '/', true, projectName, data.timestamp)
    result.Id = projectName
    result.ETag = data.hash
    var entries = new Array<Entry>(result);
    for (var i = 0, n = data.files.length; i < n; i++) {
      var file = data.files[i];
      if (!file.path) {
        // project entry is found with empty path fill in the data
        result.ETag = file.hash;
        result.LocalTimeStamp = file.timestamp;
        continue;
      }
      if (file.path) {
        file.path = '/' + file.path;
      }
      file.path = projectName + file.path;
      var lastIndexOfSlash = file.path.lastIndexOf('/');
      var name = lastIndexOfSlash < 0 ? file.path : file.path.substr(lastIndexOfSlash + 1);
      var isFile = file.type === 'file';

      var entry = new Entry(file.path, null, !isFile, name, file.timestamp)
      entry.Id = name
      entry.ETag = file.hash
      entries.push(entry)
    }

    var childrenDepthMap = <{ [key: number]: Array<Entry>; }>{};
    for (var i = 0, n = entries.length; i < n; i++) {
      var entry = entries[i];
      var depth = entry.Location.split('/').length - 1;
      if (!childrenDepthMap[depth]) {
        childrenDepthMap[depth] = [];
      }
      childrenDepthMap[depth].push(entry)
      if (depth === 0) {
        result = entry
      }
    }
    assignAncestry({}, childrenDepthMap, 0)
    for (var i = 0, n = entries.length; i < n; i++) {
      var entry = entries[i];
      entry.Location = this.rootLocation + entry.Location;
      if (entry.Directory) {
        entry.ChildrenLocation = entry.Location + '/';
      }
    }
    return result;
  }

  private getProject(projectName: string): Promise<Entry> {
    return this.stompClient.request(ProjectService.get, {
      'project': projectName
    })
      .then((data: Projects.GetResponse) => {
              return this.createOrionProject(data, projectName)
            })
  }

  /**
   * Loads all the user's workspaces. Returns a deferred that will provide the loaded
   * workspaces when ready.
   */
  public loadWorkspaces(): Deferred {
    return this.loadWorkspace(null)
  }

  public loadWorkspace(location: string): Deferred {
    var deferred = new Deferred()
    if (this.workspace != null) {
      deferred.resolve(this.workspace)
      return deferred
    }
    else {
      return promiseToDeferred(this.getWorkspace())
    }
  }

  private getWorkspace(): Promise<Entry> {
    if (this.workspace != null) {
      return Promise.resolve(this.workspace)
    }

    var workspace = new Entry(this.rootLocation, this.rootLocation, true)
    return <Promise<Entry>>this.stompClient.request(ProjectService.getAll)
      .then((data: Projects.GetAllResponse) => {
              var requests = new Array<Promise<Entry>>(data.projects.length);
              for (var i = 0, n = data.projects.length; i < n; i++) {
                requests[i] = this.getProject(data.projects[i].name)
              }

              if (requests.length == 0) {
                this.workspace = workspace
                return workspace
              }

              return Promise.all(requests).then((results: Array<Entry>) => {
                workspace.Children = results;
                for (var i = 0, n = results.length; i < n; i++) {
                  var result = results[i];
                  result.Parents = [workspace]
                  workspace.childrenCache[result.Name] = result;
                }
                this.workspace = workspace
                return workspace
              })
            })
  }

  private findFromLocation(location: string) {
    return this.getWorkspace().then((workspace: any) => {
      var result = workspace;
      var relativeLocation = location.replace(this.rootLocation, "");
      if (relativeLocation) {
        var path = relativeLocation.split('/');
        for (var i = 0; i < path.length && result; i++) {
          result = result._childrenCache ? result._childrenCache[path[i]] : null;
        }
      }
      return result;
    });
  }

  createResource(location: string, type: ResourceType, contents?: string) {
    var deferred = new Deferred()
    var normalizedPath = this.toResourceUri(location);
    var hash = sha1(contents)
    var timestamp = Date.now();
    this.findFromLocation(location).then((resource: any) => {
      if (resource) {
        deferred.reject("The resource \'" + location + "\' already exists!");
      }
      else {
        var data = {
          'project': normalizedPath.project,
          'resource': normalizedPath.path,
          'hash': hash,
          'type': type,
          'timestamp': timestamp
        };

        this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, type, hash, timestamp, contents ? contents : "", deferred);
        this.stompClient.notify(ResourceTopics.created, data);
        //This deferred is not resolved, but that is intentional.
        // It is resolved later when we get a response back for our message.
      }
    });
    return deferred;
  }

  createProject(url: string, projectName: string, serverPath: string, create: boolean) {
    var deferred = new Deferred();
    this.getWorkspace().then((workspace: any) => {
      if (workspace._childrenCache && workspace._childrenCache[projectName]) {
        deferred.reject("Project with name \'" + projectName + "\' already exists!");
      }
      else {
        var hash = sha1(projectName);
        var timestamp = Date.now();
        var location = url + projectName;
        var project = {
          Attributes: {
            ReadOnly: false,
            SymLink: false,
            Hidden: false,
            Archive: false
          },
          Name: projectName,
          Directory: true,
          ETag: hash,
          LocalTimeStamp: timestamp,
          Location: location,
          Children: <Array<any>>[],
          ChildrenLocation: location + '/',
          Parents: [workspace],
          _childrenCache: {},
          Id: projectName
        };
        if (!workspace._childrenCache) {
          workspace._childrenCache = {}
        }
        workspace._childrenCache[projectName] = project
        if (!workspace.Children) {
          workspace.Children = []
        }
        workspace.Children.push(project)
        this.stompClient.notify(ProjectTopics.created, {"project": projectName})
        deferred.resolve(project)
      }
    });
    return deferred;
  }

  /**
   * Creates a folder.
   * @param {String} parentLocation The location of the parent folder
   * @param {String} folderName The name of the folder to create
   * @return {Object} JSON representation of the created folder
   */
  createFolder(parentLocation: string, folderName: string) {
    return this.createResource(parentLocation + '/' + folderName, ResourceType.folder);
  }

  /**
   * Create a new file in a specified location. Returns a deferred that will provide
   * The new file object when ready.
   * @param {String} parentLocation The location of the parent folder
   * @param {String} fileName The name of the file to create
   * @return {Object} A deferred that will provide the new file object
   */
  createFile(parentLocation: string, fileName: string) {
    return this.createResource(parentLocation + '/' + fileName, ResourceType.file);
  }

  /**
   * Deletes a file, directory, or project.
   * @param {String} location The location of the file or directory to delete.
   */
  deleteFile(location: string) {
    return this.findFromLocation(location).then((resource: any) => {
      if (resource) {
        var parent = resource.Parents[0];
        delete parent._childrenCache[resource.Name];
        var idx = parent.Children.indexOf(resource);
        if (idx >= 0) {
          parent.Children.splice(idx, 1);
        }
        var resourceUri = this.toResourceUri(location)
        this.stompClient.notify(service.ResourceTopics.deleted, {
          'project': resourceUri.project,
          'resource': resourceUri.path,
          'timestamp': Date.now(),
          'hash': resource.ETag
        });
      }
    });
  }

  moveFile(sourceLocation: string, targetLocation: string, name: string) {
    throw "Move file not supported";
  }

  copyFile(sourceLocation: string, targetLocation: string, name: string) {
    throw "Copy file not supported";
  }

  /**
   * Returns the contents or metadata of the file at the given location.
   *
   * @param {String} location The location of the file to get contents for
   * @param {Boolean} [isMetadata] If defined and true, returns the file metadata,
   *   otherwise file contents are returned
   * @return A deferred that will be provided with the contents or metadata when available
   */
  read(location: string, isMetadata: boolean): Deferred {
    if (isMetadata) {
      return promiseToDeferred(this.findFromLocation(location))
    }

    var deferred = new Deferred();
    var resourceUri = this.toResourceUri(location);
    this.stompClient.request(ResourceService.get, {
      'project': resourceUri.project,
      'resource': resourceUri.path
    }).done((data: any) => {
      deferred.resolve(data.content)
    })
    return deferred
  }
  
  public getResource(location: string): Promise<service.GetResourceResponse> {
    return this.getResourceByUri(this.toResourceUri(location))
  }

  public getResourceByUri(uri: ResourceUri): Promise<service.GetResourceResponse> {
    return this.stompClient.request<service.GetResourceResponse>(ResourceService.get, {
      project: uri.project,
      resource: uri.path
    })
  }

  write(location: string, contents: any, args: any) {
    var deferred = new Deferred()
    var normalizedPath = this.toResourceUri(location)
    var hash = sha1(contents)
    var timestamp = Date.now()

    this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, ResourceType.file, hash, timestamp, contents, deferred)
    this.stompClient.notify(service.ResourceTopics.changed, {
      'project': normalizedPath.project,
      'resource': normalizedPath.path,
      'hash': hash,
      'timestamp': timestamp
    });
    return deferred;
  }

  remoteImport(targetLocation: string, options: any): Deferred {
    throw "Remote Import not supported";
  }

  remoteExport(sourceLocation: string, options: any): Deferred {
    throw "Remote Export not supported";
  }
}

enum ResourceType {
  file, folder
}

class FileAttributes {
  constructor(public ReadOnly: boolean = false, public SymLink: boolean = false, public Hidden: boolean = false, public Archive: boolean = false) {}
}

class Entry {
  public Length = 0
  public Children: Array<any> = []
  public Parents: Array<any>

  public childrenCache: { [key: string]: Entry; } = {}

  public Id: string
  public ETag: string

  constructor(public Location: string, public ChildrenLocation: string, public Directory: boolean = false, public Name: string = "", public LocalTimeStamp: number = Date.now(), public Attributes: FileAttributes = new FileAttributes()) {}
}

class Saved {
  constructor(public project: string, public resource: string, public type: ResourceType, hash: string, timestamp: number, content: string, deferred: any) {}
}