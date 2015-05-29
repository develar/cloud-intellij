import Promise = require("bluebird")

import bootstrap = require("orion/bootstrap")
import setup = require("edit/setup")

declare function require(s: string): void

require("edit/edit.css")

import {
  login,
  init,
  Credential,
} from "./auth"


init(true)
login().then(function () {
  bootstrap.startup().then(function(core: any) {
    var serviceRegistry = core.serviceRegistry;
    var pluginRegistry = core.pluginRegistry;
    var preferences = core.preferences;
    setup.setUpEditor(serviceRegistry, pluginRegistry, preferences, false)
  })
})