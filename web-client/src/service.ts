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
  hash: string
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
  constructor(public name: string) {
  }

  // broadcast request (liveResourcesRequested -> n liveResources direct messages)
  get responseName(): string {
    return null
  }
}

export class LiveEditTopics extends Topic {
  public static liveResourceStarted = new LiveEditTopics("liveResourceStarted")
  public static liveResourceChanged = new LiveEditTopics("liveResourceChanged")
}

export class ResourceTopics extends Topic {
  public static resourceChanged = new ResourceTopics("resourceChanged")
  public static resourceDeleted = new ResourceTopics("resourceDeleted")
  public static resourceCreated = new ResourceTopics("resourceCreated")
}

export class ProjectTopics extends Topic {
  public static projectCreated = new ResourceTopics("projectCreated")
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