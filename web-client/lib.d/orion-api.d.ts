declare module "orion-api" {
  // see fileClient.js
  export interface FileClient {
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

  export interface Validator {
    computeProblems(editorContext: any, options: any): any
  }
}