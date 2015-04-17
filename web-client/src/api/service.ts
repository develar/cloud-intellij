import orion = require("orion-api")

export class Service<R> {
  get serviceName(): string {
    throw new Error("abstract")
  }

  constructor(public name: string) {
  }
}

export class Topic {
  public response: Topic

  // responseName could be specified - broadcast request (liveResourcesRequested -> n liveResources direct messages)
  constructor(public name: string, responseExpected: boolean = false) {
    this.response = responseExpected ? new Topic(name + ".response") : null
  }
}