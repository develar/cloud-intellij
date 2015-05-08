"use strict"

import Promise = require("bluebird")
import IdePreferenceProvider = require("IdePreferenceProvider")

import PluginProvider = require("orion/plugin")

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

import {
  EditorCommand,
  EditorCommandOptions,
  EditorContext,
  } from "orion-api"

import EditorManager from "EditorManager"

import {
  FluxAuthService,
  User,
  } from "authService"

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
  //provider.registerService("orion.core.auth", authService)
  var user = authService.getUser()
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

class SelectWord implements EditorCommand {
  constructor(private stompClient: StompConnector, private fileService: FileService) {
  }

  execute(context: EditorContext, options: EditorCommandOptions): Promise<void> {
    var uri = this.fileService.toResourceUri(options.input)
    return context.getSelection()
      .then((selection) => {
        return this.stompClient.request(EditorService.selectWord, {
          project: uri.project,
          path: uri.path,
          offset: options.offset,
          selectionStart: selection.start,
          selectionEnd: selection.end
        })
      })
      .then((selection: Array<number>) => {
        if (selection == null) {
          return Promise.reject("Unable to select")
        }
        else {
          context.setSelection(selection[0], selection[1])
        }
      })
  }
}

function connect(mqHost: string, user: User, provider: PluginProvider) {
  const stompConnector = new StompConnector()

  stompConnector.connect(mqHost, user.id, user.token)
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

      var editorService = new EditorManager(stompConnector, fileService)

      provider.registerService(["orion.edit.model", "orion.edit.live", "orion.edit.contentAssist", "orion.edit.validator"], editorService, {contentType: ["text/plain"]})

      provider.registerServiceProvider("orion.edit.command", new SelectWord(stompConnector, fileService), {
        name : "Extend Selection",
        id : "EditorSelectWord",
        key : ["control W"]
      })

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