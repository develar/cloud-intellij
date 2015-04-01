package org.intellij.flux

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.MessageConnector
import org.eclipse.flux.client.ProjectTopics
import org.eclipse.flux.client.ResourceTopics
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.ProjectService
import org.eclipse.flux.client.services.ResourceService
import java.util.concurrent.ConcurrentLinkedDeque

private val LOG = Logger.getInstance("flux-intellij")

trait RepositoryListener {
  fun projectConnected(project: Project) {
  }

  fun projectDisconnected(project: Project) {
  }
}

class IntellijRepository(private val messageConnector: MessageConnector, private val username: String) {
  init {
    messageConnector.on(ResourceTopics.changed) {
      updateResource(it)
    }
    messageConnector.on(ResourceTopics.created) {
      createResource(it)
    }
    messageConnector.on(ResourceTopics.deleted) {
      deleteResource(it)
    }

    messageConnector.addService(object : ProjectService {
      override fun getAll(result: Result) {
        result.write {
          it.name("projects")
          it.beginArray()
          for (project in ProjectManager.getInstance().getOpenProjects()) {
            it.beginObject()
            it.name("name").value(project.getName())
            it.endObject()
          }
          it.endArray()
        }
      }

      override fun get(projectName: String, result: Result) {
        result.write { writer ->
          val project = findReferencedProject(projectName)
          if (project == null) {
            writer.name("error").value("not found")
            return
          }

          writer.name("files").beginArray()
          val baseDir = project.getBaseDir()
          ProjectRootManager.getInstance(project).getFileIndex().iterateContent(object : ContentIterator {
            override public fun processFile(virtualFile: VirtualFile): Boolean {
              val path = VfsUtilCore.getRelativePath(virtualFile, baseDir!!)
              val timestamp: Long
              val hash: String
              val type: String
              try {
                val cachedDocument = if (virtualFile.isDirectory()) null else FileDocumentManager.getInstance().getCachedDocument(virtualFile)
                timestamp = if (cachedDocument != null) cachedDocument.getModificationStamp() else virtualFile.getModificationStamp()
                hash = if (cachedDocument != null) DigestUtils.sha1Hex(cachedDocument.getText()) else if (virtualFile.isDirectory()) "0" else DigestUtils.sha1Hex(virtualFile.getInputStream())
                type = if (virtualFile.isDirectory()) "folder" else "file"
              }
              catch (e: Throwable) {
                LOG.error(e)
                return true
              }

              writer.beginObject();
              writer.name("path").value(path)
              writer.name("timestamp").value(timestamp)
              writer.name("hash").value(hash)
              writer.name("type").value(type)
              writer.endObject();
              return true
            }
          })
          writer.endArray()
        }
      }
    })

    messageConnector.addService(object : ResourceService {
      override fun get(projectName: String, resourcePath: String, hash: String?, result: Result) {
        if (resourcePath.startsWith("classpath:")) {
          getClasspathResource(projectName, resourcePath, hash, result)
        }
        else {
          getResource(projectName, resourcePath, hash, result)
        }
      }
    })

    ProjectManager.getInstance().addProjectManagerListener(object : ProjectManagerAdapter() {
      override public fun projectOpened(project: Project) {
        if (project.isDefault()) {
          return
        }

        sendProjectConnectedMessage(project.getName())
        notifyProjectConnected(project)

        // sync Flux concept cannot be used now — project could be (and should be) under version control, so, before sync, we should update project from VCS.
        if (false) {
          syncConnectedProject(project.getName())
        }
      }

      override public fun projectClosing(project: Project) {
        notifyProjectDisconnected(project)

        messageConnector.notify(ProjectTopics.disconnected) {
          it.name("project").value(project.getName())
        }
      }
    })
  }

  private fun updateResource(request: Map<String, Any>) {
    val username = request.get("username") as String
    val projectName = request.get("project") as String
    val resourcePath = request.get("resource") as String
    val updateTimestamp = request.get("timestamp") as Long
    val updateHash = request.get("hash") as String?

    // ConnectedProject connectedProject = this.syncedProjects.get(projectName);
    if (this.username == username /*&& connectedProject != null*/) {
      val resource = findReferencedFile(resourcePath, projectName)
      var stored = false

      if (resource != null) {
        if (!resource.isDirectory()) {
          //                        String localHash = connectedProject.getHash(resourcePath);
          //                        long localTimestamp = connectedProject.getTimestamp(resourcePath);
          val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(resource)
          val localHash = if (cachedDocument != null) {
            DigestUtils.sha1Hex(cachedDocument.getText())
          }
          else {
            DigestUtils.sha1Hex(resource.getInputStream())
          }
          val localTimestamp = if (cachedDocument != null) cachedDocument.getModificationStamp() else resource.getModificationStamp()

          if (!Comparing.equal(localHash, updateHash) && localTimestamp < updateTimestamp) {
            val newResourceContent = request.get("content") as String

            if (cachedDocument != null) {
              cachedDocument.setText(newResourceContent)
            }
            else {
              VfsUtil.saveText(resource, newResourceContent)
            }
            stored = true
          }
        }
      }
      else {
        val newResourceContent = request.get("content") as String
        val i = resourcePath.lastIndexOf('/')
        val resourceDir = findReferencedFile(resourcePath.substring(0, i), projectName)
        val newFileName = resourcePath.substring(i + 1)
        if (resourceDir != null) {
          VfsUtil.saveText(resourceDir.createChildData(this, newFileName), newResourceContent)
        }
      }

      if (stored) {
        messageConnector.notify(ResourceTopics.saved) {
          it.name("project").value(projectName)
          it.name("resource").value(resourcePath)
          it.name("timestamp").value(updateTimestamp)
          it.name("hash").value(updateHash)
        }
      }
    }
  }

  public fun createResource(request: Map<String, Any>) {
    val a = 1
    /*try {
            final String username = request.getString("username");
            final String projectName = request.getString("project");
            final String resourcePath = request.getString("resource");
            final long updateTimestamp = request.getLong("timestamp");
            final String updateHash = request.optString("hash");
            final String type = request.optString("type");

            ConnectedProject connectedProject = this.syncedProjects.get(projectName);
            if (this.username.equals(username) && connectedProject != null) {
                Module project = connectedProject.getProject();
                IResource resource = project.findMember(resourcePath);

                if (resource == null) {
                    if ("folder".equals(type)) {
                        IFolder newFolder = project.getFolder(resourcePath);

                        connectedProject.setHash(resourcePath, updateHash);
                        connectedProject.setTimestamp(resourcePath, updateTimestamp);

                        newFolder.create(true, true, null);
                        newFolder.setLocalTimeStamp(updateTimestamp);
                    }
                    else if ("file".equals(type)) {
                        JSONObject message = new JSONObject();
                        message.put("callback_id", GET_RESOURCE_CALLBACK);
                        message.put("username", this.username);
                        message.put("project", projectName);
                        message.put("resource", resourcePath);
                        message.put("timestamp", updateTimestamp);
                        message.put("hash", updateHash);

                        messagingConnector.send("getResourceRequest", message);
                    }
                }
                else {
                    // TODO
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }*/
  }

  public fun deleteResource(request: Map<String, Any>) {
    val a = 1
    /*try {
            final String username = request.getString("username");
            final String projectName = request.getString("project");
            final String resourcePath = request.getString("resource");
            final long deletedTimestamp = request.getLong("timestamp");

            ConnectedProject connectedProject = this.syncedProjects.get(projectName);
            if (this.username.equals(username) && connectedProject != null) {
                Module project = connectedProject.getProject();
                IResource resource = project.findMember(resourcePath);

                if (resource != null && resource.exists() && (resource instanceof IFile || resource instanceof IFolder)) {
                    long localTimestamp = connectedProject.getTimestamp(resourcePath);

                    if (localTimestamp < deletedTimestamp) {
                        resource.delete(true, null);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }*/
  }

  private fun getResource(projectName: String, resourcePath: String, hash: String?, result: Result) {
    //                Module project = connectedProject.getProject();
    //
    //                if (request.has("timestamp") && request.getLong("timestamp") != connectedProject.getTimestamp(resourcePath)) {
    //                    return;
    //                }
    //
    //                IResource resource = project.findMember(resourcePath);
    result.write {writer ->
      val resource = findReferencedFile(resourcePath, projectName)
      if (resource == null) {
        writer.name("error").value("not found")
        return
      }

      val token = ReadAction.start()
      try {
        val document = FileDocumentManager.getInstance().getDocument(resource)
        //message.put("hash", connectedProject.getHash(resourcePath));
        if (resource.isDirectory()) {
          writer.name("type").value("folder")
        }
        else {
          writer.name("lastSaved").value(resource.getTimeStamp())

          val shaHex = if (document != null) DigestUtils.sha1Hex(document.getText()) else DigestUtils.sha1Hex(resource.getInputStream())
          if (hash == shaHex) {
            return
          }

          writer.name("hash").value(shaHex)
          writer.name("content").value(if (document != null) document.getText() else "")
          writer.name("type").value("file")
        }
      }
      finally {
        token.finish()
      }
    }
  }

  private fun getClasspathResource(projectName: String, resourcePath: String, hash: String?, result: Result) {
    //ConnectedProject connectedProject = this.syncedProjects.get(projectName);


    //                IJavaProject javaProject = JavaCore.create(connectedProject.getProject());
    //                if (javaProject != null) {
    //                    IType type = javaProject.findType(typeName);
    //                    IClassFile classFile = type.getClassFile();
    //                    if (classFile != null && classFile.getSourceRange() != null) {

    result.write {
      val typeName = resourcePath.substring("classpath:/".length())
      val fileByPath = JarFileSystem.getInstance().findFileByPath(typeName)
      if (fileByPath == null) {
        it.name("error").value("not found")
        return
      }

      val content = BinaryFileTypeDecompilers.INSTANCE.forFileType(fileByPath.getFileType()).decompile(fileByPath).toString()
      it.name("project").value(projectName)
      it.name("resource").value(resourcePath)
      it.name("readonly").value(true)
      it.name("content").value(content)
      it.name("hash").value(DigestUtils.sha1Hex(content))
      it.name("type").value("file")
    }
  }

  private fun getResourceResponse(response: Map<String, Any>) {
    val projectName = response.get("project") as String
    val resourcePath = response.get("resource") as String
    val updateTimestamp = response.get("timestamp") as Long
    val updateHash = response.get("hash") as String

    var stored = false

    //Module project = connectedProject.getProject();
    //IResource resource = project.findMember(resourcePath);
    val resource = findReferencedFile(resourcePath, projectName)

    if (resource != null) {
      if (!resource.isDirectory()) {
        //                        String localHash = connectedProject.getHash(resourcePath);
        //                        long localTimestamp = connectedProject.getTimestamp(resourcePath);
        val localHash = DigestUtils.sha1Hex(resource.getInputStream())
        val localTimestamp = resource.getModificationStamp()

        if (!Comparing.equal(localHash, updateHash) && localTimestamp < updateTimestamp) {
          val newResourceContent = response.get("content") as String
          VfsUtil.saveText(resource, newResourceContent)
          stored = true
        }
      }
    }
    else {
      val newResourceContent = response.get("content") as String
      // todo create new file
      //                    VfsUtil.createChildSequent()
      //
      //                    connectedProject.setHash(resourcePath, updateHash);
      //                    connectedProject.setTimestamp(resourcePath, updateTimestamp);
      //
      //                    newFile.create(new ByteArrayInputStream(newResourceContent.getBytes()), true, null);
      //                    newFile.setLocalTimeStamp(updateTimestamp);
      //                    stored = true;
    }

    if (stored) {
      messageConnector.notify(ResourceTopics.saved) {
        it.name("project").value(projectName)
        it.name("resource").value(resourcePath)
        it.name("timestamp").value(updateTimestamp)
        it.name("hash").value(updateHash)
      }
    }
  }

//  public fun getMetadata(request: Map<String, Any>) {
//            com.intellij.openapi.application.AccessToken accessToken = ReadAction.start();
//            try {
//                final String username = request.getString("username");
//                final int callbackID = request.getInt("callback_id");
//                final String sender = request.getString("requestSenderID");
//                final String projectName = request.getString("project");
//                final String resourcePath = request.getString("resource");
//                //errorAnalyzerService.sendProblems(username, callbackID, sender, projectName, resourcePath, "getMetadataResponse");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//  }


  private val repositoryListeners = ConcurrentLinkedDeque<RepositoryListener>()

  public fun addRepositoryListener(listener: RepositoryListener) {
    repositoryListeners.add(listener)
  }

  public fun removeRepositoryListener(listener: RepositoryListener) {
    repositoryListeners.remove(listener)
  }

  protected fun notifyProjectConnected(project: Project) {
    for (listener in repositoryListeners) {
      listener.projectConnected(project)
    }
  }

  protected fun notifyProjectDisconnected(project: Project) {
    for (listener in repositoryListeners) {
      listener.projectDisconnected(project)
    }
  }

  protected fun syncConnectedProject(projectName: String) {
    messageConnector.request(ProjectService.Methods.get) {
      it.name("project").value(projectName)
      it.name("includeDeleted").value(true)
    }.done { response ->
      val projectName = response.get("project") as String
      [suppress("UNCHECKED_CAST")]
      val files = response.get("files") as List<Map<String, Any>>
      val deleted = response.get("deleted") as List<*>?

      //ConnectedProject connectedProject = this.syncedProjects.get(projectName);
      for (i in 0..files.size() - 1) {
        val resource = files.get(i)

        val resourcePath = resource.get("path") as String
        val timestamp = resource.get("timestamp") as Long

        val type = resource.get("type") as String?
        val hash = resource.get("hash") as String?

        //                    boolean newFile = type != null && type.equals("file") && !connectedProject.containsResource(resourcePath);
        //                    boolean updatedFileTimestamp =  type != null && type.equals("file") && connectedProject.containsResource(resourcePath)
        //                            && connectedProject.getHash(resourcePath).equals(hash) && connectedProject.getTimestamp(resourcePath) < timestamp;
        //                    boolean updatedFile = type != null && type.equals("file") && connectedProject.containsResource(resourcePath)
        //                            && !connectedProject.getHash(resourcePath).equals(hash) && connectedProject.getTimestamp(resourcePath) < timestamp;

        //                    if (newFile || updatedFile) {
        //                        JSONObject message = new JSONObject();
        //                        message.put("callback_id", GET_RESOURCE_CALLBACK);
        //                        message.put("project", projectName);
        //                        message.put("username", this.username);
        //                        message.put("resource", resourcePath);
        //                        message.put("timestamp", timestamp);
        //                        message.put("hash", hash);
        //
        //                        messagingConnector.send("getResourceRequest", message);
        //                    }
        //
        //                    if (updatedFileTimestamp) {
        //                        connectedProject.setTimestamp(resourcePath, timestamp);
        //                        IResource file  = connectedProject.getProject().findMember(resourcePath);
        //                        file.setLocalTimeStamp(timestamp);
        //                    }
        //
        //                    boolean newFolder = type != null && type.equals("folder") && !connectedProject.containsResource(resourcePath);
        //                    boolean updatedFolder = type != null && type.equals("folder") && connectedProject.containsResource(resourcePath)
        //                            && !connectedProject.getHash(resourcePath).equals(hash) && connectedProject.getTimestamp(resourcePath) < timestamp;
        //
        //                    if (newFolder) {
        //                        Module project = connectedProject.getProject();
        //                        IFolder folder = project.getFolder(resourcePath);
        //
        //                        connectedProject.setHash(resourcePath, hash);
        //                        connectedProject.setTimestamp(resourcePath, timestamp);
        //
        //                        folder.create(true, true, null);
        //                        folder.setLocalTimeStamp(timestamp);
        //                    }
        //                    else if (updatedFolder) {
        //                    }
        //                }
        //
        //                if (deleted != null) {
        //                    for (int i = 0; i < deleted.length(); i++) {
        //                        JSONObject deletedResource = deleted.getJSONObject(i);
        //
        //                        String resourcePath = deletedResource.getString("path");
        //                        long deletedTimestamp = deletedResource.getLong("timestamp");
        //
        //                        Module project = connectedProject.getProject();
        //                        IResource resource = project.findMember(resourcePath);
        //
        //                        if (resource != null && resource.exists() && (resource instanceof IFile || resource instanceof IFolder)) {
        //                            long localTimestamp = connectedProject.getTimestamp(resourcePath);
        //
        //                            if (localTimestamp < deletedTimestamp) {
        //                                resource.delete(true, null);
        //                            }
        //                        }
        //                    }
      }
    }
  }

  protected fun sendProjectConnectedMessage(projectName: String) {
    messageConnector.notify(ProjectTopics.connected) {
      it.name("project").value(projectName)
    }
  }
}
