import {
  AuthService,
  } from "orion-api"

import oauth = require("hello")

export class FluxAuthService implements AuthService {
  private static DATA_KEY = "user"

  getLabel(): string {
    return "IntelliJ Flux"
  }

  getKey(): string {
    return "OAuth"
  }

  getUser(): Promise<User | Promise<User>> {
    var serialized = localStorage.getItem(FluxAuthService.DATA_KEY)
    var user: User
    try {
      user = JSON.parse(serialized)
    }
    catch (e) {
      console.warn("User data exists, but invalid", serialized, e)
      localStorage.removeItem(FluxAuthService.DATA_KEY)
      return Promise.reject(e)
    }

    if (user == null || user.provider == null) {
      if (user != null) {
        console.log("User data exists, but invalid", user)
        localStorage.removeItem(FluxAuthService.DATA_KEY)
      }
      return Promise.resolve<User>(null)
    }

    var provider = oauth.use(user.provider)
    return provider.login({force: false})
      .then((result) => {
        user.token = result.authResponse.access_token
        if (user.name != null) {
          return user
        }

        return provider.api("/me")
          .then((result) => {
            user.name = result.login
            localStorage.setItem("user", JSON.stringify({
              name: user.name,
              provider: user.provider
            }))
            return user
          })
      })
  }

  getAuthForm(): string {
    return "/auth/login.html"
  }

  logout(): Promise<any> {
    localStorage.removeItem("user")
    localStorage.removeItem("hello")
    if (document.cookie.indexOf("userData=") !== -1) {
      document.cookie = "userData=; expires=Thu, 01 Jan 1970 00:00:01 GMT;"
    }

    // todo real sign out from provider
    return Promise.resolve(null)
  }
}

export interface User {
  name: string
  provider: string
  token: string
}

export function providerToPrefix(provider: string): string {
  switch (provider) {
    case "jetbrains":
      return "jb"

    case "github":
      return "gh"

    case "google":
      return "g"

    case "facebook":
      return "fb"

    default:
      throw new Error("Unknown provider: " + provider)
  }
}