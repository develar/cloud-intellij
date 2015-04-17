package org.intellij.flux

import com.intellij.openapi.project.ProjectManager
import org.jetbrains.flux.ProjectService
import org.jetbrains.flux.Result

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