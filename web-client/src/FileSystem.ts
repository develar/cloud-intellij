/// <reference path="../typings/stomp.d.ts" />
/// <reference path="../typings/cryptojs/cryptojs.d.ts" />

//import ignored = require("CryptoJS")
import Deferred = require("Deferred")
import stompClient = require("stompClient")

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
    var child = childrenDepthMap[depth][i];
    if (depth > 0) {
      var parentLocation = child.Location.substr(0, child.Location.lastIndexOf('/'));
      if (parents[parentLocation]) {
        var parent = parents[parentLocation];
        if (!parent.Children) {
          parent.Children = [];
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

// see fileClient.js
interface FileClient {
  /**
   * Obtains the children of a remote resource
   * @param location The location of the item to obtain children for
   * @return A deferred that will provide the array of child objects when complete
   */
  fetchChildren(location: string): Deferred

  /**
   * Loads the workspace with the given id and sets it to be the current
   * workspace for the IDE. The workspace is created if none already exists.
   * @param {String} location the location of the workspace to load
   */
  loadWorkspace(location: string): Deferred

  /**
   * Loads all the user's workspaces. Returns a deferred that will provide the loaded
   * workspaces when ready.
   */
  loadWorkspaces(): Deferred

  /**
   * Adds a project to a workspace.
   * @param {String} url The workspace location
   * @param {String} projectName the human-readable name of the project
   * @param {String} serverPath The optional path of the project on the server.
   * @param {Boolean} create If true, the project is created on the server file system if it doesn't already exist
   */
  createProject(url: string, projectName: string, serverPath: string, create: boolean): void

  /**
   * Creates a folder.
   * @param {String} parentLocation The location of the parent folder
   * @param {String} folderName The name of the folder to create
   * @return {Object} JSON representation of the created folder
   */
  createFolder(parentLocation: string, folderName: string): any

  /**
   * Create a new file in a specified location. Returns a deferred that will provide
   * The new file object when ready.
   * @param {String} parentLocation The location of the parent folder
   * @param {String} fileName The name of the file to create
   * @return {Object} A deferred that will provide the new file object
   */
  createFile(parentLocation: string, fileName: string): Deferred

  /**
   * Deletes a file, directory, or project.
   * @param {String} location The location of the file or directory to delete.
   */
  deleteFile(location: string): void

  /**
   * Moves a file or directory.
   * @param {String} sourceLocation The location of the file or directory to move.
   * @param {String} targetLocation The location of the target folder.
   * @param {String} [name] The name of the destination file or directory in the case of a rename
   */
  moveFile(sourceLocation: string, targetLocation: string, name: string): void

  /**
   * Copies a file or directory.
   * @param {String} sourceLocation The location of the file or directory to copy.
   * @param {String} targetLocation The location of the target folder.
   * @param {String} [name] The name of the destination file or directory in the case of a rename
   */
  copyFile(sourceLocation: string, targetLocation: string, name: string): void

  /**
   * Writes the contents or metadata of the file at the given location.
   *
   * @param {String} location The location of the file to set contents for
   * @param {String|Object} contents The content string, or metadata object to write
   * @param {String|Object} args Additional arguments used during write operation (i.e. ETag)
   * @return A deferred for chaining events after the write completes with new metadata object
   */
  write(location: string, contents: any, args: any): Deferred


  /**
   * Returns the contents or metadata of the file at the given location.
   *
   * @param {String} location The location of the file to get contents for
   * @param {Boolean} [isMetadata] If defined and true, returns the file metadata,
   *   otherwise file contents are returned
   * @return A deferred that will be provided with the contents or metadata when available
   */
  read(location: string, isMetadata: boolean): Deferred

  /**
   * Returns the blob contents of the file at the given location.
   *
   * @param {String} location The location of the file to get contents for
   * @return A deferred that will be provided with the blob contents when available
   */
  //readBlob(location)

  /**
   * Imports file and directory contents from another server
   *
   * @param {String} targetLocation The location of the folder to import into
   * @param {Object} options An object specifying the import parameters
   * @return A deferred for chaining events after the import completes
   */
  remoteImport(targetLocation: string, options: any): Deferred

  /**
   * Exports file and directory contents to another server
   *
   * @param {String} sourceLocation The location of the folder to export from
   * @param {Object} options An object specifying the export parameters
   * @return A deferred for chaining events after the export completes
   */
  remoteExport(sourceLocation: string, options: any): Deferred
}

class FileSystem implements FileClient {
  private workspace: any
  private saves: { [key: string]: Saved; } = {};

  /**
   * @class Provides operations on files, folders, and projects.
   * @name FileServiceImpl
   */
  constructor(private stompClient: stompClient.StompConnector, private _rootLocation: string) {}

  private normalizeLocation(location: string) {
    if (!location) {
      location = "/"
    }
    else {
      location = location.replace(this._rootLocation, "")
    }
    var indexOfDelimiter = location.indexOf('/');
    var project = indexOfDelimiter < 0 ? location : location.substr(0, indexOfDelimiter);
    location = indexOfDelimiter < 0 ? undefined : location.substr(indexOfDelimiter + 1);
    return {'project': project, 'path': location};
  }

  fetchChildren(location: string): Deferred {
    if (location.charAt(location.length - 1) === '/') {
      location = location.substr(0, location.length - 1)
    }
    return promiseToDeferred(this.findFromLocation(location).then(function (parent: any) {
      return parent && parent.Children ? parent.Children : [];
    }))
  }

  private createOrionProject(data: Projects.ProjectsGetResponse, projectName: string): Entry {
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
      entry.Location = this._rootLocation + entry.Location;
      if (entry.Directory) {
        entry.ChildrenLocation = entry.Location + '/';
      }
    }
    return result;
  }

  private getProject(projectName: string): Promise<Entry> {
    return this.stompClient.request("projects", "get", {
      'project': projectName
    })
      .then((data: Projects.ProjectsGetResponse) => {
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

    var workspace = new Entry(this._rootLocation, this._rootLocation, true)
    return <Promise<Entry>>this.stompClient.request("projects", "getAll")
      .then((data: Projects.ProjectsGetAllResponse) => {
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
      var relativeLocation = location.replace(this._rootLocation, "");
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
    var normalizedPath = this.normalizeLocation(location);
    var hash = CryptoJS.SHA1(contents).toString(CryptoJS.enc.Hex);
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
        this.stompClient.notify("resourceCreated", data);
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
        var hash = CryptoJS.SHA1(projectName).toString(CryptoJS.enc.Hex);
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
          workspace._childrenCache = {};
        }
        workspace._childrenCache[projectName] = project;
        if (!workspace.Children) {
          workspace.Children = [];
        }
        workspace.Children.push(project);
        this.stompClient.notify("projectCreated", {
          'project': projectName
        });
        deferred.resolve(project);
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
    var self = this;
    return this.findFromLocation(location).then((resource: any) => {
      if (resource) {
        var parent = resource.Parents[0];
        delete parent._childrenCache[resource.Name];
        var idx = parent.Children.indexOf(resource);
        if (idx >= 0) {
          parent.Children.splice(idx, 1);
        }
        var normalizedPath = self.normalizeLocation(location);
        this.stompClient.notify("resourceDeleted", {
          'project': normalizedPath.project,
          'resource': normalizedPath.path,
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

    var normalizedPath = this.normalizeLocation(location);
    var deferred = new Deferred();
    this.stompClient.request("resources", "get", {
      'project': normalizedPath.project,
      'resource': normalizedPath.path
    }).done((data: any) => {
      deferred.resolve(data.content)
    })
    return deferred;
  }

  write(location: string, contents: any, args: any) {
    var deferred = new Deferred()
    var normalizedPath = this.normalizeLocation(location)
    var hash = CryptoJS.SHA1(contents).toString(CryptoJS.enc.Hex)
    var timestamp = Date.now()

    this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, ResourceType.file, hash, timestamp, contents, deferred)
    this.stompClient.notify("resourceChanged", {
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

declare module Projects {
  interface ProjectsGetResponse {
    files: Array<FileItem>

    timestamp: number
    hash: string
  }

  interface FileItem {
    timestamp: number
    path: string
    hash: string
    type: string
  }

  interface ProjectsGetAllResponse {
    projects: Array<any>
  }
}

export = FileSystem;