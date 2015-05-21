/// <reference path="../lib.d/bluebird.d.ts" />
/// <amd-dependency path="bluebird" name="Promise"/>

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

var oauth: Auth = null

export function init(implicit: boolean = false) {
  var jbHub = endsWith(location.host, ".dev") ? new JbHub("5e190e8e-31c4-462d-b74a-be8025988c8f", "hub.dev") : new JbHub("0799e9c5-849d-40e8-bbc6-5d5d6c9e711f")
  oauth = new Auth(jbHub, new Store<Session>(), {
    response_type: implicit ? "token" : "code",
  })
}

export interface Credential {
  id: string
  token: string
}

export function login(): Promise<Credential> {
  return oauth.login({force: false})
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