export class Service<R> {
  get serviceName(): string {
    throw new Error("abstract")
  }

  constructor(public name: string) {
  }
}

export class ProjectService<R> extends Service<R> {
  get serviceName(): string {
    return "project";
  }

  public static getAll = new ProjectService<Projects.GetAllResponse>("getAll")
  public static get = new ProjectService<Projects.GetResponse>("get")
}

export class ResourceService<R> extends Service<R> {
  get serviceName(): string {
    return "resource";
  }

  public static get = new ResourceService<GetResourceResponse>("get")
}

export interface GetResourceResponse {
  // file of folder
  type: string

  // if file
  /**
   * Last saved time. It is not last modified time - client in most cases cannot provide it.
   */
  lastSaved: number
  hash: string
  /**
   * Actual content. Could be not last saved content, but live content.
   */
  content: string
}

export class EditorService<R> extends Service<R> {
  get serviceName(): string {
    return "editor";
  }

  public static quickfix = new EditorService<any>("quickfix")
  public static javadoc = new EditorService<any>("javadoc")

  public static metadata = new EditorService<any>("metadata")
  public static contentAssist = new EditorService<any>("contentAssist")
}

export class Topic {
  public responseTopic: string

  // responseName could be specified - broadcast request (liveResourcesRequested -> n liveResources direct messages)
  constructor(public name: string, responseExpected: boolean = false) {
    this.responseTopic = responseExpected ? name + ".response" : null
  }
}

export class EditorTopics {
  public static started = new Topic("editor.started", true)
  public static startedResponse = new Topic(EditorTopics.started.responseTopic)

  public static changed = new Topic("editor.changed")

  public static metadataChanged = new Topic("editor.metadataChanged")
}

// contains project and resource because it is a broadcast response (we subscribe to event, we don't use Promise) - we need to identify resource
export interface EditorStartedResponse {
  project: string
  resource: string
  content: string
  hash: string
}

// contains project and resource because it is a broadcast response (we subscribe to event, we don't use Promise) - we need to identify resource
export interface EditorStarted {
  project: string
  resource: string
  hash: string
}

export class ResourceTopics {
  public static changed = new Topic("resource.changed")
  public static deleted = new Topic("resource.deleted")
  public static created = new Topic("resource.created")
}

export class ProjectTopics {
  public static created = new Topic("project.created")
}

export declare module Projects {
  interface GetResponse {
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

  interface GetAllResponse {
    projects: Array<any>
  }
}