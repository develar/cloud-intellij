"use strict"

import sha1 = require("sha1")
import stompClient = require("stompClient")
import service = require("service")
import Promise = require("bluebird")
import orion = require("orion-api")

import ProjectService = service.ProjectService
import ResourceService = service.ResourceService
import ResourceTopics = service.ResourceTopics
import ProjectTopics = service.ProjectTopics
import ProjectGetResponse = service.ProjectGetResponse

import File = orion.File
import Directory = orion.Directory

export class ResourceUri {
  constructor(public project: string, public path?: string) {
  }

  public equals(other?: ResourceUri): boolean {
    return other != null && other.project === this.project && other.path === this.path
  }
}

class VirtualFileSystem {
  constructor(public root: Directory) {
  }

  public findFileOrKnownParent(location: string): File {
    if (location === this.root.Location) {
      return this.root
    }

    var parent = this.root
    outer: while (true) {
      if (parent.Children == null) {
        break
      }

      for (let child of parent.Children) {
        if (child.Location === location) {
          return child
        }

        if (child instanceof Directory) {
          var childrenLocation = child.Location
          if (location.length > childrenLocation.length && location[childrenLocation.length] === "/" && location.indexOf(childrenLocation) === 0) {
            parent = <Directory>child
            continue outer
          }
        }
      }

      console.error("Cannot find " + location + " in the existing parent directory children")
    }
    return parent
  }

  /**
   * Orion File API is ugly - we must calculate parents
   */
  public locationToParent(location: string, isDirectory: boolean = true): Directory {
    // find in the existing data
    var file = this.findFileOrKnownParent(location)
    if (file.Location === location) {
      return isDirectory ? <Directory>file : file.Parents[0]
    }

    var parent = <Directory>file
    while (true) {
      var nameStart = parent.Location.length + 1;
      var slashPosition = location.indexOf('/', nameStart)
      if (!isDirectory && slashPosition < 0) {
        break
      }
      parent = new Directory(location.substring(nameStart, slashPosition < 0 ? location.length : slashPosition), parent)
      if (slashPosition < 0) {
        break
      }
    }
    return parent
  }
}

export class FileService implements orion.FileClient {
  private vfs: VirtualFileSystem
  private saves: { [key: string]: Saved; } = {};
  
  constructor(private stompClient: stompClient.StompConnector, private rootLocation: string) {
    this.vfs = new VirtualFileSystem(new Directory(rootLocation))
  }

  public toResourceUri(location: string): ResourceUri {
    var path = location.substring(this.rootLocation.length + 1)
    var indexOfDelimiter = path.indexOf('/')
    var project = indexOfDelimiter < 0 ? path : path.substring(0, indexOfDelimiter)
    return new ResourceUri(project, indexOfDelimiter < 0 ? null : path.substring(indexOfDelimiter + 1))
  }

  public isOurResource(location?: string) {
    return location != null && location.indexOf(this.rootLocation) === 0
  }

  fetchChildren(location: string): Promise<Array<File>> {
    if (location === this.rootLocation) {
      return this.stompClient.request(ProjectService.getAll)
        .then((result: service.ProjectGetAllResult) => {
          var children = new Array<File>(result.projects.length)
          for (var i = 0, n = result.projects.length; i < n; i++) {
            var name = result.projects[i].name;
            children[i] = new Directory(name, this.vfs.root)
          }
          this.vfs.root.Children = children
          return children
        })
    }

    if (location.charAt(location.length - 1) === '/') {
      console.warn("Location normalized", location)
      location = location.substr(0, location.length - 1)
    }

    const parent = this.vfs.locationToParent(location)
    if (parent.Children != null) {
      return Promise.resolve(parent.Children)
    }

    const uri = this.toResourceUri(location)
    return this.stompClient.request(ResourceService.get, uri)
      .then((result: service.GetResourceResponse) => {
        var children = "topLevelChildren" in result ? result.topLevelChildren : result.children
        var n = children.length
        var orionChildren = new Array<File>(n)
        for (var i = 0; i < n; i++) {
          var child = children[i]
          if ("lastSaved" in child) {
            orionChildren[i] = FileService.createFileFromMetadata(child, parent)
          }
          else {
            orionChildren[i] = new Directory(child.name, parent)
          }
        }
        parent.Children = orionChildren
        return orionChildren
      })
  }

  private static createFileFromMetadata(descriptor: service.GetResourceResponse, parent: Directory) {
    var file = new File(descriptor.name, parent)
    file.Length = descriptor.length
    file.ETag = descriptor.hash
    file.LocalTimeStamp = descriptor.lastSaved
    return file
  }

  //private createOrionProject(result: ProjectGetResponse, projectName: string): File {
  //  var project = orion.createDirectory(projectName, projectName)
  //  var entries = new Array<File>(result);
  //  for (var i = 0, n = result.files.length; i < n; i++) {
  //    var file = result.files[i];
  //    if (!file.path) {
  //      // project entry is found with empty path fill in the data
  //      project.ETag = file.hash;
  //      project.LocalTimeStamp = file.lastSaved;
  //      continue;
  //    }
  //    if (file.path) {
  //      file.path = '/' + file.path;
  //    }
  //    file.path = projectName + file.path;
  //    var lastIndexOfSlash = file.path.lastIndexOf('/');
  //    var name = lastIndexOfSlash < 0 ? file.path : file.path.substr(lastIndexOfSlash + 1);
  //    var isFile = file.type === 'file';
  //
  //    //var entry = new File(file.path, null, !isFile, name, file.lastSaved)
  //    //entry.Id = name
  //    //entry.ETag = file.hash
  //    //entries.push(entry)
  //  }
  //
  //  var childrenDepthMap = <{ [key: number]: Array<File>; }>{};
  //  for (var i = 0, n = entries.length; i < n; i++) {
  //    var entry = entries[i]
  //    var depth = entry.Location.split('/').length - 1
  //    if (!childrenDepthMap[depth]) {
  //      childrenDepthMap[depth] = []
  //    }
  //    childrenDepthMap[depth].push(entry)
  //    if (depth === 0) {
  //      result = entry
  //    }
  //  }
  //  assignAncestry({}, childrenDepthMap, 0)
  //  for (var i = 0, n = entries.length; i < n; i++) {
  //    var entry = entries[i];
  //    entry.Location = this.rootLocation + entry.Location
  //    if (entry.Directory) {
  //      entry.ChildrenLocation = entry.Location + '/'
  //    }
  //  }
  //  return result;
  //}

  //private getProject(projectName: string): Promise<File> {
  //  return this.stompClient.request(ProjectService.get, {
  //    'project': projectName
  //  })
  //    .then((data: ProjectGetResponse) => {
  //            return this.createOrionProject(data, projectName)
  //          })
  //}

  /**
   * Loads all the user's workspaces. Returns a deferred that will provide the loaded
   * workspaces when ready.
   */
  public loadWorkspaces(): Promise<File> {
    return this.loadWorkspace("")
  }

  public loadWorkspace(location: string): Promise<File> {
    // orion doesn't check it, will be reference error, so, we do it
    if (this.vfs.root.Children == null) {
      return this.fetchChildren(this.rootLocation)
        .then(() => {
          return this.vfs.root
        })
    }
    else {
      return Promise.resolve(this.vfs.root)
    }
  }

  private getWorkspace(): Promise<orion.File> {
    return <Promise<File>>this.stompClient.request(ProjectService.getAll)
      .then((result: service.ProjectGetAllResult) => {
              var children = new Array<File>(result.projects.length)
              for (var i = 0, n = result.projects.length; i < n; i++) {
                var name = result.projects[i].name;
                children[i] = new Directory(name)
              }
              this.vfs.root.Children = children
              return this.vfs.root
            })
  }

  private findFromLocation(location: string) {
    return this.getWorkspace().then((workspace: any) => {
      var result = workspace;
      var relativeLocation = location.replace(this.rootLocation, "")
      if (relativeLocation) {
        var path = relativeLocation.split('/');
        for (var i = 0; i < path.length && result; i++) {
          result = result._childrenCache ? result._childrenCache[path[i]] : null
        }
      }
      return result;
    })
  }

  createResource(location: string, type: ResourceType, contents?: string): Promise<any> {
    var uri = this.toResourceUri(location)
    var hash = sha1(contents)
    var timestamp = Date.now()
    return this.findFromLocation(location).then((resource: any) => {
      if (resource) {
        return Promise.reject("The resource \'" + location + "\' already exists!");
      }
      else {
        var data = {
          'project': uri.project,
          'resource': uri.path,
          'hash': hash,
          'type': type,
          'timestamp': timestamp
        };

        //this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, type, hash, timestamp, contents ? contents : "", deferred);
        this.stompClient.notify(ResourceTopics.created, data);
        //This deferred is not resolved, but that is intentional.
        // It is resolved later when we get a response back for our message.
        return Promise.reject("unsupported")
      }
    })
  }

  createProject(url: string, projectName: string, serverPath: string, create: boolean) {
    return this.getWorkspace().then((workspace: any) => {
      if (workspace._childrenCache && workspace._childrenCache[projectName]) {
        Promise.reject("Project with name \'" + projectName + "\' already exists!");
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
        return project
      }
    })
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
  createFile(parentLocation: string, fileName: string): Promise<any> {
    return this.createResource(parentLocation + '/' + fileName, ResourceType.file);
  }

  deleteFile(location: string): Promise<void> {
    var uri = this.toResourceUri(location)
    this.stompClient.notify(service.ResourceTopics.deleted, uri)
    // todo prohibit project remove
    return Promise.resolve()
  }

  moveFile(sourceLocation: string, targetLocation: string, name: string) {
    throw "Move file not supported";
  }

  copyFile(sourceLocation: string, targetLocation: string, name: string) {
    throw "Copy file not supported";
  }

  read(location: string, isMetadata: boolean): Promise<string | File> {
    if (isMetadata) {
      let file = this.vfs.findFileOrKnownParent(location)
      if (file.Location === location) {
        return Promise.resolve(file)
      }
    }

    const uri = this.toResourceUri(location)
    return this.stompClient.request(ResourceService.get, {project: uri.project, path: uri.path, contents: !isMetadata})
      .then((result: service.GetResourceResponse) => {
        if (!isMetadata) {
          var content = result.content
          if (content == null) {
            console.error("Content must be not null")
          }
          return content
        }

        const isFile = "lastSaved" in result
        const parent = this.vfs.locationToParent(location, !isFile)
        if (isFile) {
          return FileService.createFileFromMetadata(result, parent)
        }
        else {
          return parent
        }
      })
  }
  
  public getResource(location: string): Promise<service.GetResourceResponse> {
    return this.getResourceByUri(this.toResourceUri(location))
  }

  public getResourceByUri(uri: ResourceUri): Promise<service.GetResourceResponse> {
    return this.stompClient.request<service.GetResourceResponse>(ResourceService.get, {
      project: uri.project,
      path: uri.path
    })
  }

  write(location: string, contents: any, args: any) {
    var normalizedPath = this.toResourceUri(location)
    var hash = sha1(contents)
    var timestamp = Date.now()

    //this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, ResourceType.file, hash, timestamp, contents, new Promise())
    this.stompClient.notify(service.ResourceTopics.changed, {
      'project': normalizedPath.project,
      'resource': normalizedPath.path,
      'hash': hash,
      'timestamp': timestamp
    })
    return Promise.reject("unsupported")
  }

  remoteImport(targetLocation: string, options: any): Promise<any> {
    throw "Remote Import not supported";
  }

  remoteExport(sourceLocation: string, options: any): Promise<any> {
    throw "Remote Export not supported";
  }
}

enum ResourceType {
  file, folder
}

class Saved {
  constructor(public project: string, public resource: string, public type: ResourceType, hash: string, timestamp: number, content: string, deferred: any) {}
}