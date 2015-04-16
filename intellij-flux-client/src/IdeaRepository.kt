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
import org.jetbrains.json.jsonReader
import org.jetbrains.json.map
import org.jetbrains.json.nextNullableString
import java.util.concurrent.ConcurrentLinkedDeque

private val LOG = Logger.getInstance("flux-idea")

trait RepositoryListener {
  fun projectConnected(project: Project) {
  }
}

class IdeaRepository(private val messageConnector: MessageConnector, private val username: String) {
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

    messageConnector.addService(IdeaProjectService())
    messageConnector.addService(IdeaResourceService())

    ProjectManager.getInstance().addProjectManagerListener(object : ProjectManagerAdapter() {
      override public fun projectOpened(project: Project) {
        if (project.isDefault()) {
          return
        }

        sendProjectConnectedMessage(project.getName())
        notifyProjectConnected(project)
      }

      override public fun projectClosing(project: Project) {
        messageConnector.notify(ProjectTopics.disconnected) {
          "project"(project.getName())
        }
      }
    })
  }

  private fun updateResource(request: ByteArray) {
    var project: String? = null
    var path: String? = null
    var updateTimestamp = 0L
    var updateHash: String? = null
    var content: String? = null
    request.jsonReader().map {
      when (nextName()) {
        "project" -> project = nextString()
        "path" -> path = nextString()
        "timestamp" -> updateTimestamp = nextLong()
        "hash" -> updateHash = nextNullableString()
        "content" -> content = nextString()
        else -> skipValue()
      }
    }

    // ConnectedProject connectedProject = this.syncedProjects.get(projectName);
    if (this.username == username /*&& connectedProject != null*/) {
      val resource = findReferencedFile(path!!, project!!)
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
            val newResourceContent = content!!
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
        val newResourceContent = content
        val i = path!!.lastIndexOf('/')
        val resourceDir = findReferencedFile(path!!.substring(0, i), project!!)
        val newFileName = path!!.substring(i + 1)
        if (resourceDir != null) {
          VfsUtil.saveText(resourceDir.createChildData(this, newFileName), newResourceContent!!)
        }
      }

      if (stored) {
        messageConnector.notify(ResourceTopics.saved) {
          "project"(project)
          "path"(path)
          "timestamp"(updateTimestamp)
          "hash"(updateHash)
        }
      }
    }
  }

  public fun createResource(request: ByteArray) {
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

  public fun deleteResource(request: ByteArray) {
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

  protected fun sendProjectConnectedMessage(projectName: String) {
    messageConnector.notify(ProjectTopics.connected) {
      "project"(projectName)
    }
  }
}
