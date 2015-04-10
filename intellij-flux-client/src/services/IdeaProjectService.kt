package org.intellij.flux

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.codec.digest.DigestUtils
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.ProjectService

class IdeaProjectService : ProjectService {
  override fun getAll(result: Result) {
    result.write {
      array("projects") {
        for (project in ProjectManager.getInstance().getOpenProjects()) {
          map {
            "name"(project.getName())
          }
        }
      }
    }
  }
}