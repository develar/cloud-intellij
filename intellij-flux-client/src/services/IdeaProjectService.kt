package org.intellij.flux

import com.intellij.openapi.project.ProjectManager
import org.eclipse.flux.client.Result
import org.eclipse.flux.client.services.ProjectService

class IdeaProjectService : ProjectService {
  override fun getAll(result: Result) {
    result.map {
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