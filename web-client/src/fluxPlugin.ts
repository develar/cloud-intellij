import Promise = require("bluebird")
import stompClient = require("stompClient")
import fileSystem = require("FileSystem")
import Editor = require("Editor")
import IdePreferenceProvider = require("IdePreferenceProvider")
import service = require("service")

import PluginProvider = require("orion/plugin")

const hostname = window.location.hostname
let mqHost: string
if (/^\d+\.\d+\.\d+\.\d+$/.test(location.host)) {
  // ip address - dev machine
  mqHost = hostname + ":" + 15674
}
else {
  mqHost = `mq.${hostname}`
}

const stompConnector = new stompClient.StompConnector()
stompConnector.connect(mqHost, "dev", "dev").done(() => {
  const rootLocation = "ide"
  let headers = {
    'Name': "IntelliJ Flux",
    'Version': "0.1",
    'Description': "IntelliJ Flux Integration",
    'top': rootLocation,
    // orion client: c12f972	07/08/14 18:35	change orion file client pattern to "/file" instead of "/"
    'pattern': "^(" + rootLocation + ")|(/file)"
  }
  var provider = new PluginProvider(headers)

  var fileService = new fileSystem.FileService(stompConnector, rootLocation);
  provider.registerService("orion.core.file", fileService, headers)

  var editorService = new Editor(stompConnector, fileService)

  provider.registerService(["orion.edit.model", "orion.edit.live", "orion.edit.contentAssist", "orion.edit.validator"], editorService, {contentType: ["text/plain"]})

  provider.registerService("orion.edit.command", {
    execute: function (editorContext: any, context: any): void {
      if (context.annotation != null && context.annotation.id) {
        editorService.applyQuickfix(editorContext, context)
      }
    }
  }, {
    id: "orion.css.quickfix.zeroQualifier",
    image: "../images/compare-addition.gif",
    scopeId: "orion.edit.quickfix",
    name: "Apply quickfix",
    contentType: ["text/x-java-source"],
    tooltip: "Apply Quick Fix",
    validationProperties: []
  })

  Promise.all([
    stompConnector.request(service.ResourceService.contentTypes)
      .then((result: Array<service.ContentTypeDescriptor>) => {
        for (let contentType of result) {
          //noinspection ReservedWordAsName
          if (contentType.extends == null) {
            contentType.extends = "text/plain"
          }
        }

        //noinspection SpellCheckingInspection
        provider.registerServiceProvider("orion.core.contenttype", {}, {contentTypes: result})
        provider.registerServiceProvider("orion.edit.highlighter", editorService.eventTarget, {type: "highlighter", contentType: result})
      }),
    stompConnector.request(service.EditorService.styles)
      .then((result: service.EditorStyles) => {
        provider.registerService("orion.core.preference.provider", new IdePreferenceProvider(result))
      })
  ])
    .done(() => {
      provider.connect()
    })
})