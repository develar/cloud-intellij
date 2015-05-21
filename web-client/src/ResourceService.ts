/// <reference path="../lib.d/bluebird.d.ts" />
/// <amd-dependency path="bluebird" name="Promise"/>

import sha1 = require("sha1")

import stompClient = require("./stompClient")

import {
  File,
  Directory,
  FileClient,
} from "./orion-api"

import {
  ResourceService,
  ResourceTopics,
  GetResourceResponse,
} from "./api/resource"

import {
  ProjectTopics,
  ProjectService,
  ProjectGetAllResult,
} from "./api/project"

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

  public static createChildren(parent: Directory, children: Array<GetResourceResponse>): Array<File> {
    var n = children.length
    var orionChildren = new Array<File>(n)
    for (var i = 0; i < n; i++) {
      var child = children[i]
      if ("lastSaved" in child) {
        orionChildren[i] = VirtualFileSystem.createFileFromMetadata(child, parent)
      }
      else {
        var directory = new Directory(child.name, parent);
        orionChildren[i] = directory
        if (child.children != null) {
          VirtualFileSystem.createChildren(directory, child.children)
        }
      }
    }
    parent.Children = orionChildren
    return orionChildren
  }

  public static createFileFromMetadata(descriptor: GetResourceResponse, parent: Directory) {
    var file = new File(descriptor.name, parent)
    file.Length = descriptor.length
    file.ETag = descriptor.hash
    file.LocalTimeStamp = descriptor.lastSaved
    return file
  }
}

export class FileService implements FileClient {
  private vfs: VirtualFileSystem
  
  constructor(private stompClient: stompClient.StompConnector, private rootLocation: string) {
    this.vfs = new VirtualFileSystem(new Directory(rootLocation))
  }

  public toResourceUri(location: string): ResourceUri {
    var path = location.substring(this.rootLocation.length + 1)
    var indexOfDelimiter = path.indexOf('/')
    var project = indexOfDelimiter < 0 ? path : path.substring(0, indexOfDelimiter)
    return new ResourceUri(project, indexOfDelimiter < 0 ? null : path.substring(indexOfDelimiter + 1))
  }

  fetchChildren(location: string): Promise<Array<File>> {
    if (location === this.rootLocation) {
      return this.stompClient.request(ProjectService.getAll)
        .then((result: ProjectGetAllResult) => {
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
      .then((result: GetResourceResponse) => {
        var children = result.children
        // if project has only one top-level directory - merge it
        if (children.length === 1 && parent.Parents[0] == this.vfs.root) {
          children = children[0].children
        }
        return VirtualFileSystem.createChildren(parent, children)
      })
  }

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

  private getWorkspace(): Promise<File> {
    return <Promise<File>>this.stompClient.request(ProjectService.getAll)
      .then((result: ProjectGetAllResult) => {
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
    this.stompClient.notify(ResourceTopics.deleted, uri)
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
      .then((result: GetResourceResponse) => {
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
          return VirtualFileSystem.createFileFromMetadata(result, parent)
        }
        else {
          return parent
        }
      })
  }

  write(location: string, contents: any, args: any) {
    var normalizedPath = this.toResourceUri(location)
    var hash = sha1(contents)
    var timestamp = Date.now()

    //this.saves[location] = new Saved(normalizedPath.project, normalizedPath.path, ResourceType.file, hash, timestamp, contents, new Promise())
    this.stompClient.notify(ResourceTopics.changed, {
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