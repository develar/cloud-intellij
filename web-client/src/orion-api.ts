import Promise = require("bluebird")

export interface PreferenceProvider {
  get(name: string, force?: boolean): any

  put(name: string, data: any): void

  remove(name: string, key: string): void
}

// see fileClient.js
export interface FileClient {
  /**
   * Obtains the children of a remote resource
   * @param location The location of the item to obtain children for
   * @return A deferred that will provide the array of child objects when complete
   */
  fetchChildren(location: string): Promise<Array<File>>

  /**
   * Loads the workspace with the given id and sets it to be the current
   * workspace for the IDE. The workspace is created if none already exists.
   * @param {String} location the location of the workspace to load
   */
  loadWorkspace(location: string): Promise<File>

  /**
   * Loads all the user's workspaces. Returns a deferred that will provide the loaded
   * workspaces when ready.
   */
  loadWorkspaces(): Promise<File>

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
  createFile(parentLocation: string, fileName: string): Promise<any>

  /**
   * Deletes a file, directory, or project.
   * @param {String} location The location of the file or directory to delete.
   */
  deleteFile(location: string): Promise<void>

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
  write(location: string, contents: String|Object, args: any): Promise<any>


  /**
   * Returns the contents or metadata of the file at the given location.
   *
   * @param {String} location The location of the file to get contents for
   * @param {Boolean} [isMetadata] If defined and true, returns the file metadata,
   *   otherwise file contents are returned
   * @return A deferred that will be provided with the contents or metadata when available
   */
  read(location: string, isMetadata: boolean): Promise<string | File>

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
  remoteImport(targetLocation: string, options: any): Promise<any>

  /**
   * Exports file and directory contents to another server
   *
   * @param {String} sourceLocation The location of the folder to export from
   * @param {Object} options An object specifying the export parameters
   * @return A deferred for chaining events after the export completes
   */
  remoteExport(sourceLocation: string, options: any): Promise<any>
}

export class FileAttributes {
  constructor(public Archive: boolean = false, public ReadOnly: boolean = false, public Executable: boolean = false, public SymLink: boolean = false, public Hidden: boolean = false) {}
}

// https://wiki.eclipse.org/Orion/Server_API/File_API#File
export class File {
  public ETag: string
  public LocalTimeStamp: number
  public Location: string

  public Attributes: FileAttributes
  public Children: Array<File>
  public Parents: Array<Directory>

  public Length = 0

  constructor(public Name: string, parent?: Directory, public Directory: boolean = false) {
    this.Location = parent == null ? Name : (parent.Location + '/' + Name)

    // https://wiki.eclipse.org/Orion/Documentation/Developer_Guide/Core_client_services#orion.core.file
    // The returned file object should have a zero-length 'Parents' array to designate it as the root of the filesystem
    if (parent == null) {
      this.Parents = []
    }
    else {
      this.Parents = parent.Parents.slice(0)
      this.Parents.splice(0, 0, parent)
    }
  }
}

export class Directory extends File {
  public ChildrenLocation: string

  constructor(name: string, parent?: Directory) {
    super(name, parent, true)

    this.ChildrenLocation = this.Location
  }
}

export interface EditorOptions {
  title: string
}

export interface ContentAssistOptions extends EditorOptions {
  offset: number
  prefix: string
  selection: any
}

export interface Problems {
  problems: Array<any>
}

export interface Validator {
  computeProblems(editorContext: EditorContext, options: EditorOptions): Promise<Problems>
}

export interface ContentAssist {
  computeContentAssist(editorContext: EditorContext, options: ContentAssistOptions): Promise<Array<any>>
}

export interface EditorMarker {
  description: string
  line: number
  severity: string
  start: number
  end: number
}

export interface EditorFileMetadata {
  name: string
  location: string
}

export interface EditorContext {
  setText(value: string, start?: number, end?: number): Promise<any>

  getText(): Promise<string>

  showMarkers(markers: Array<EditorMarker>): void

  getFileMetadata(): Promise<EditorFileMetadata>
}

export interface ServiceFile {
  name: string
  location: string
}

export interface ServiceEvent {
  file: ServiceFile
}

export interface ModelChangingEvent extends ServiceEvent {
  text: string
  start: number
  removedCharCount: number
  addedCharCount: number
  removedLineCount: number
  addedLineCount: number
}

export interface LiveEditor {
  // Promise<void> must be returned to keep reference to editorContext (otherwise it will be invalidated)
  startEdit(editorContext: EditorContext, context: any): Promise<void>

  endEdit(location: string): void

  onModelChanging(event: ModelChangingEvent): void
}
