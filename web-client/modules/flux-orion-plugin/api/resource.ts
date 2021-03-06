import {
  Service,
  Topic,
} from "./service"

export class ResourceTopics {
  public static changed = new Topic("resource.changed")
  public static deleted = new Topic("resource.deleted")
  public static created = new Topic("resource.created")
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
