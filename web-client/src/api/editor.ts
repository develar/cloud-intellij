import orion = require("orion-api")
import service = require("service")

import Topic = service.Topic

export class EditorService<R> extends service.Service<R> {
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