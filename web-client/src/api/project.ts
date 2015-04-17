import {
    Service,
    Topic,
    } from "api/service"

export class ProjectTopics {
  public static created = new Topic("project.created")
}

export class ProjectService<R> extends Service<R> {
  get serviceName(): string {
    return "project";
  }

  public static getAll = new ProjectService<ProjectGetAllResult>("getAll")
}

export interface ProjectDescriptor {
  name: string
}

export interface ProjectGetAllResult {
  projects: Array<ProjectDescriptor>
}