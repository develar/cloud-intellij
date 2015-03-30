export class Service {
  get serviceName(): string {
    throw new Error("abstract")
  }

  constructor(public name: string) {
  }
}

export class ProjectService extends Service {
  get serviceName(): string {
    return "project";
  }

  public static getAll = new ProjectService("getAll")
  public static get = new ProjectService("get")
}

export class ResourceService extends Service {
  get serviceName(): string {
    return "resource";
  }

  public static get = new ResourceService("get")
}

export declare module Projects {
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