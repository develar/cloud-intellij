import Promise = require("bluebird")

import {
  Auth,
  Session,
  LoginResultState,
} from "oauth/oauth"

import {
  Store,
} from "oauth/store"

import {
  JbHub,
} from "oauth/jbHub"

function endsWith(str: string, suffix: string): boolean {
  return str.indexOf(suffix, str.length - suffix.length) !== -1
}

declare const OAUTH_CLIENT_ID_DEV: string
declare const OAUTH_CLIENT_ID_PROD: string

export var oauth: Auth = null

export function init(implicit: boolean = false) {
  var jbHub = endsWith(location.host, ".dev") ? new JbHub(OAUTH_CLIENT_ID_DEV, "hub.dev") : new JbHub(OAUTH_CLIENT_ID_PROD)
  oauth = new Auth(jbHub, new Store<Session>(implicit ? localStorage : sessionStorage), {
    response_type: implicit ? "token" : "code",
  })
}

export interface Credential {
  id: string
  token: string
}

export function login(): Promise<Credential> {
  return oauth.login()
    .then((result) => {
      if (result.state == LoginResultState.REDIRECTED) {
        return Promise.reject(null)
      }
      else if (result.state == LoginResultState.UNCHANGED) {
        let id = localStorage.getItem("user")
        if (id != null) {
          return Promise.resolve({id: id, token: result.session.access_token})
        }
      }

      let token = result.session.access_token
      // if session expires, we update user id in any case, to avoid stale data (user can log in using different credentials)
      return oauth.api("me?fields=id")
        .then((result) => {
          let id = result.id
          localStorage.setItem("user", id)
          return Promise.resolve({id: id, token: token})
        })
    })
}