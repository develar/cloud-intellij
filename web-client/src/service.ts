import orion = require("orion-api")

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

  public static getAll = new ProjectService<ProjectGetAllResult>("getAll")
  public static get = new ProjectService<ProjectGetResponse>("get")
}

export class ResourceService<R> extends Service<R> {
  get serviceName(): string {
    return "resource"
  }

  public static get = new ResourceService<GetResourceResponse>("get")

  public static contentTypes = new ResourceService<Array<ContentTypeDescriptor>>("contentTypes")
}

// https://wiki.eclipse.org/Orion/Documentation/Developer_Guide/Plugging_into_the_navigator#orion.core.contenttype
export interface ContentTypeDescriptor {
  id: string
  name: string
  extension: Array<string>

  extends?: string

  image?: string
}

export interface GetResourceResponse {
  // if described as child of directory or requested as "contents: false"
  name?: string

  // if directory
  children: Array<GetResourceResponse>

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

  length: number
}

export class EditorService<R> extends Service<R> {
  get serviceName(): string {
    return "editor"
  }

  public static quickfix = new EditorService<any>("quickfix")

  public static problems = new EditorService<orion.Problems>("problems")
  public static contentAssist = new EditorService<any>("contentAssist")

  public static styles = new EditorService<EditorStyles>("styles")
}

export interface EditorColors {
  CARET_ROW_COLOR?: string
  SELECTION_BACKGROUND?: string
}

export interface EditorStyles {
  colors: EditorColors

  EDITOR_FONT_SIZE: number
  EDITOR_FONT_NAME: string
}

export class Topic {
  public response: Topic

  // responseName could be specified - broadcast request (liveResourcesRequested -> n liveResources direct messages)
  constructor(public name: string, responseExpected: boolean = false) {
    this.response = responseExpected ? new Topic(name + ".response") : null
  }
}

export class EditorTopics {
  public static started = new Topic("editor.started", true)
  // response on changed sends only designated "main" idea service (currently not implemented - all sends response)
  public static changed = new Topic("editor.changed", true)

  public static metadataChanged = new Topic("editor.metadataChanged")
}

// contains project and resource because it is a broadcast response (we subscribe to event, we don't use Promise) - we need to identify resource
export interface EditorEventResponse {
  project: string
  path: string
}

export interface DocumentChanged extends EditorEventResponse {
  offset: number
  removedCharCount: number
  newFragment: string
}

export interface EditorStarted extends EditorEventResponse {
  hash: string
}

export interface EditorStartedResponse extends EditorEventResponse {
  content: string
  hash: string
}

export interface EditorMetadataChanged extends EditorEventResponse {
  markers: Array<orion.EditorMarker>
}

export class ResourceTopics {
  public static changed = new Topic("resource.changed")
  public static deleted = new Topic("resource.deleted")
  public static created = new Topic("resource.created")
}

export class ProjectTopics {
  public static created = new Topic("project.created")
}

export interface FileDescriptor {
  lastSaved: number
  path: string
  hash: string
  type: string
}

export interface ProjectDescriptor {
  name: string
}

export interface ProjectGetResponse {
  files: Array<FileDescriptor>
}

export interface ProjectGetAllResult {
  projects: Array<ProjectDescriptor>
}