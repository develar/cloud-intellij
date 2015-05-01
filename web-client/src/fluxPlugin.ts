import Promise = require("bluebird")
import Editor = require("Editor")
import IdePreferenceProvider = require("IdePreferenceProvider")

import PluginProvider = require("orion/plugin")

import {
  AuthService,
  } from "orion-api"

import {
  FileService,
  } from "ResourceService"

import {
  EditorService,
  EditorStyles,
  } from "api/editor"

import {
  ResourceService,
  ContentTypeDescriptor,
  } from "api/resource"

import {
  StompConnector,
  } from "stompClient"

interface User {
  name: string
  provider: string
  token: string
}

class FluxAuthService implements AuthService {
  getLabel(): string {
    return "IntelliJ Flux"
  }

  getKey(): string {
    return "OAuth"
  }

  getUser(): User {
    var serialized = localStorage.getItem("user")
    try {
      var user = JSON.parse(serialized)
      if (user != null && user.name != null && user.provider != null && user.token != null) {
        return user
      }
      else {
        if (user != null) {
          console.log("User data exists, but invalid", user)
        }
      }
    }
    catch (e) {
      console.warn("User data exists, but invalid", serialized)
    }
    return null
  }

  getAuthForm(): string {
    return "/auth/login.html"
  }

  logout(): Promise<any> {
    localStorage.removeItem("user")
    localStorage.removeItem("hello")
    // todo real sign out from provider
    return Promise.resolve(null)
  }
}

function endsWith(str: string, suffix: string): boolean {
  return str.indexOf(suffix, str.length - suffix.length) !== -1
}

function checkAuthAndConnect() {
  var provider = new PluginProvider({
    Name: "IntelliJ Flux",
    Version: "0.1",
    Description: "IntelliJ Flux Integration",
  })

  var authService = new FluxAuthService()
  provider.registerService("orion.core.auth", authService)
  var user = authService.getUser()
  if (user == null) {
    // bootstrap will use our authService and redirect to login page
    console.log("User is not authenticated, redirect to login page")
    provider.connect()
    return
  }

  const hostname = window.location.hostname
  let mqHost: string
  if (/^\d+\.\d+\.\d+\.\d+$/.test(location.host) || location.host.indexOf('.') === -1 || endsWith(location.host, ".dev")) {
    // ip address or local domain - dev machine
    mqHost = hostname + ":" + 4443
  }
  else {
    mqHost = "mq." + hostname
  }

  connect(mqHost, user, provider)
}

function authProviderToPrefix(provider: string): string {
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

function connect(mqHost: string, user: User, provider: PluginProvider) {
  const stompConnector = new StompConnector()

  stompConnector.connect(mqHost, authProviderToPrefix(user.provider) + "_" + user.name, user.token)
    .done(() => {
      provider.registerService("orion.core.preference.provider", new IdePreferenceProvider(stompConnector.request<EditorStyles>(EditorService.styles)))

      const rootLocation = "ide"
      var fileService = new FileService(stompConnector, rootLocation);
      provider.registerService("orion.core.file", fileService, {
        Name: "IntelliJ Flux",
        top: rootLocation,
        // orion client: c12f972	07/08/14 18:35	change orion file client pattern to "/file" instead of "/"
        pattern: "^(" + rootLocation + ")|(/file)"
      })

      var editorService = new Editor(stompConnector, fileService)

      provider.registerService(["orion.edit.model", "orion.edit.live", "orion.edit.contentAssist", "orion.edit.validator"], editorService, {contentType: ["text/plain"]})

      //provider.registerService("orion.edit.command", {
      //  execute: function (editorContext: any, context: any): void {
      //    if (context.annotation != null && context.annotation.id) {
      //      editorService.applyQuickfix(editorContext, context)
      //    }
      //  }
      //}, {
      //  id: "orion.css.quickfix.zeroQualifier",
      //  image: "../images/compare-addition.gif",
      //  scopeId: "orion.edit.quickfix",
      //  name: "Apply quickfix",
      //  contentType: ["text/x-java-source"],
      //  tooltip: "Apply Quick Fix",
      //  validationProperties: []
      //})

      stompConnector.request(ResourceService.contentTypes)
        .then((result: Array<ContentTypeDescriptor>) => {
          for (let contentType of result) {
            //noinspection ReservedWordAsName
            if (contentType.extends == null) {
              contentType.extends = "text/plain"
            }
          }

          provider.registerServiceProvider("orion.core.contenttype", {}, {contentTypes: result})
          provider.registerServiceProvider("orion.edit.highlighter", editorService.eventTarget, {
            type: "highlighter",
            contentType: result
          })

          provider.connect()
        })
    })
}

checkAuthAndConnect()