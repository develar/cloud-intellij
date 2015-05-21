import Promise = require("bluebird")

import {
  AuthService,
} from "./orion-api"

function getFromStore<T>(key: string): T {
  var serialized = localStorage.getItem(key)
  var data: T
  try {
    data = JSON.parse(serialized)
  }
  catch (e) {
    console.warn("User data exists, but invalid", serialized, e)
    localStorage.removeItem(key)
    throw e
  }
  return data
}

export class FluxAuthService implements AuthService {
  getLabel(): string {
    return "IntelliJ Flux"
  }

  getKey(): string {
    return "OAuth"
  }

  getUser(): User {
    var session: any = getFromStore("auth")
    session = session == null ? null : session.jbHub
    if (session == null) {
      throw new Error("must be checked before")
    }

    var userId: string = localStorage.getItem("user")
    if (userId == null) {
      throw new Error("must be checked before")
    }
    return {id: userId, token: session.access_token}
  }

  getAuthForm(): string {
    throw new Error("Must not be called")
  }

  logout(): Promise<any> {
    localStorage.removeItem("user")
    localStorage.removeItem("auth")
    if (document.cookie.indexOf("userData=") !== -1) {
      document.cookie = "userData=; expires=Thu, 01 Jan 1970 00:00:01 GMT;"
    }
    return Promise.resolve()
  }
}

export interface User {
  id: string
  token: string
}