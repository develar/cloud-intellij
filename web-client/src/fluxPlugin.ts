import Promise = require("bluebird")
import stompClient = require("stompClient")
import fileSystem = require("FileSystem")
import Editor = require("Editor")
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
    'Name': "Flux",
    'Version': "0.1",
    'Description': "Flux Integration",
    'top': rootLocation,
    // orion client: c12f972	07/08/14 18:35	change orion file client pattern to "/file" instead of "/"
    'pattern': "^(" + rootLocation + ")|(/file)"
  }
  var provider = new PluginProvider(headers)

  var taskCount = 2
  stompConnector.request(service.ResourceService.contentTypes)
    .done((result: Array<service.ContentTypeDescriptor>) => {
      for (let contentType of result) {
        //noinspection ReservedWordAsName
        if (contentType.extends == null) {
          contentType.extends = "text/plain"
        }
      }

      //noinspection SpellCheckingInspection
      provider.registerServiceProvider("orion.core.contenttype", {}, {contentTypes: result})
      provider.registerServiceProvider("orion.edit.highlighter", {}, {type: "highlighter", contentType: result})

      taskCount--
      if (taskCount === 0) {
        provider.connect()
      }
    })

  var fileService = new fileSystem.FileService(stompConnector, rootLocation);
  provider.registerService("orion.core.file", fileService, headers)

  provider.registerServiceProvider("orion.page.link.category", null, {
    id: "flux",
    name: "Flux",
//		nameKey: "Flux",
//		nls: "orion-plugin/nls/messages",
    imageDataURI: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAABuwAAAbsBOuzj4gAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAEqSURBVDiNpdMxS9thEAbwX/6Gbm6CkrVjoYuDhygoWfwEpW4dikLngpMgguDqEopjp4JfIHMHc5CWTlla2kkEhwwuBRFNhyQQ038SxZvu3ueeu/e5971Kr9fzHCuexX5KgcyslJ1XZknIzB18RA3n2I6IbmmBzJzHKjoRcZGZb/AFo92/ox4R1w8kZOYifqCJ35n5CZ/HyLCMD8OgOgIc4uXAf4HdKcpWhs7oEOtTCNMLZObGSPfH2FJmvoZKq9Waw1f94Y3bH/05HJRgHWxWsTeBfIu3+IZ1/0t8hf0CWxOueRQR7Yjo4T3+luS8K7BQAlziNDOHr9RFoyRvvkCrBKgNiqwN4rb+bxy3nwXOcDdBxqxVbRQR0dQf1hXuxxLKFugGv3AcESf/AFmNUKHs4+bxAAAAAElFTkSuQmCC",
    order: 5
  })

  var editorService = new Editor(stompConnector, fileService)

  // requires our fork - orion doesn't support pattern, only content-type
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

  taskCount--
  if (taskCount === 0) {
    provider.connect()
  }
})