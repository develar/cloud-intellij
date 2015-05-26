import Promise = require("bluebird")

import {
  Store,
  JsonStorage,
} from "./store"

import {
  parseQueryString,
  extend,
  toUrl,
  unique,
  toQueryString,
  iframe,
  merge,
  xhr,
  isEmpty,
  encodeQuery,
  computeDiff,
} from "./util"

export enum LoginResultState {
  REDIRECTED, UNCHANGED, CHANGED
}

export interface ProviderOauth {
  auth: string
  grant: string
}

export interface Provider {
  clientId: string

  providerId: string

  oauth: ProviderOauth

  base: string

  scope: any
  scopeDelimiter?: string

  logout(): void
}

interface Options {
  page_uri?: string
  redirect_uri?: string

  display?: string

  scopes?: string[]

  response_type?: string
  oauth_proxy?: string
}

export class Auth {
  private settings: Options = {
    redirect_uri: window.location.href.split('#')[0],
    response_type: "token",
    display: "page",

    oauth_proxy: "/oauth",
  }

  private loginStateStorage = new JsonStorage<LoginState>("loginState", sessionStorage)

  constructor(private provider: Provider, private store: Store<Session>, options?: Options) {
    // Update the default settings with this one.
    if (options != null) {
      extend(this.settings, options)

      // do this immediately in case the browser changes the current path.
      if ("redirect_uri" in options) {
        this.settings.redirect_uri = toUrl(options.redirect_uri).href;
      }
    }

    this.responseHandler()
  }

  private computeEffectiveScope(scopes?: string[], oldScopes?: string[]): string[] {
    if (scopes == null) {
      scopes = []
    }
    else {
      scopes = scopes.slice()
    }

    scopes.push("basic")

    // append scopes from a previous session. This helps keep app credentials constant, avoiding having to keep tabs on what scopes are authorized
    if (oldScopes != null) {
      scopes = scopes.concat(oldScopes)
    }

    for (let i = 0, n = scopes.length; i < n; i++) {
      let mapped = this.provider.scope[scopes[i]]
      if (mapped != null) {
        scopes[i] = mapped
      }
    }
    return unique(scopes)
  }

  public login(force: Boolean = false, state: string = null): Promise<LoginResult> {
    var options = this.settings
    var session = this.getSession()
    if (session != null && session.client_id !== this.provider.clientId) {
      this.store.set(this.provider.providerId, null)
      session = null
    }

    var oldScopes: string[] = session == null ? null : session.scope
    var scopes = this.computeEffectiveScope(options.scopes, oldScopes)
    if (!force && session != null && session.access_token && session.expires > (Date.now() / 1000)) {
      var diff = oldScopes == null ? null : computeDiff(oldScopes, scopes)
      if (diff == null || diff.length === 0) {
        var isNew = session.isNew
        if (isNew) {
          session.isNew = false
          this.store.set(this.provider.providerId, session)
        }
        return Promise.resolve(new LoginResult(isNew ? LoginResultState.CHANGED : LoginResultState.UNCHANGED, session))
      }
    }

    this.loginStateStorage.set({
      client_id: this.provider.clientId,
      network: this.provider.providerId,
    })

    var queryString = {
      client_id: this.provider.clientId,
      response_type: options.response_type,
      redirect_uri: toUrl(options.response_type === "token" ? options.redirect_uri : options.oauth_proxy).href,
      scope: scopes.join(this.provider.scopeDelimiter || ","),
      refresh_token: <string>null,
      state: state
    }

    var url: string
    if (options.display === "none" && session != null && session.refresh_token != null) {
      queryString.refresh_token = session.refresh_token
      url = toQueryString(options.oauth_proxy, queryString)
    }
    else {
      url = toQueryString(this.provider.oauth.auth, queryString)
    }

    if (options.display === "none") {
      iframe(url)
      throw new Error("unsupported")
    }
    else {
      window.location.assign(url)
      return Promise.resolve(new LoginResult(LoginResultState.REDIRECTED))
    }
  }

  // Remove any data associated with a given service
  // @param string name of the service
  // @param function callback
  public logout(force: Boolean = false): Promise<any> {
    var session = this.getSession()
    if (session == null) {
      return Promise.resolve()
    }

    var callback = () => {
      this.store.set(this.provider.providerId, null)
    }

    if (force) {
      var logout = this.provider.logout
      // todo check
      if (logout != null) {
        if (typeof logout === 'function') {
          logout()
        }
        // If logout is a string then assume URL and open in iframe.
        if (typeof (logout) === 'string') {
          iframe(logout);
        }
      }
    }

    callback()
  }

  /*
   Returns all the sessions that are subscribed too
   @param string optional, name of the service to get information about.
   */
  public getSession(): Session {
    return this.store.get(this.provider.providerId)
  }

  public api(url: string, method: string = "get"): Promise<any> {
    var session = this.getSession()
    var headers: { [key: string]: string; } = {
      Authorization: "Bearer " + session.access_token,
      Accept: "application/json"
    }

    var split: string[] = url.split("?");
    var path: string = split[0]
    if (method in this.provider) {
      //noinspection UnnecessaryLocalVariableJS
      let foo: any = this.provider
      let mapped: string = foo[method][path]
      if (mapped != null) {
        url = url.replace(path, mapped)
      }
    }

    return new Promise((resolve, reject) => {
      xhr(method, this.provider.base + url, function (r: any, headers: any) {
        if (r === true) {
          r = {success: true}
        }

        if (method === "delete") {
          r = (r == null || isEmpty(r)) ? {success: true} : r
        }
        if (r == null || "error" in r) {
          reject(r)
        }
        else {
          resolve(r)
        }
      }, headers)
    })
  }

  private responseHandler(): void {
    var location = window.location
    var response: OAuthResponse = readAuthResponseFromUrl()
    if (response == null) {
      return
    }

    var state = this.loginStateStorage.get()
    if (response.error != null) {
      var error = new Error(response.error_message || response.error_description)
      error.name = response.error
      throw error
    }

    if (response.access_token != null) {
      this.loginStateStorage.set(null)

      // github token never expires
      var expiresIn = state.network === "github" ? null : response.expires_in
      var parsedExpiresIn = expiresIn == null ? 0 : parseInt(expiresIn, 10)

      this.store.set(state.network, {
        client_id: state.client_id,
        access_token: response.access_token,
        expires: (Date.now() / 1000) + (parsedExpiresIn === 0 ? (60 * 60 * 24 * 365) : parsedExpiresIn),
        scope: response.scope.split(","),
        refresh_token: response.refresh_token,
        isNew: true,
      })
      setHash("")
    }
  }
}

function setHash(hash: string): any {
  if (history.replaceState == null) {
    window.location.hash = hash
  }
  else {
    history.replaceState(null, null, [window.location.pathname, window.location.search].join("") + "#" + hash)
  }
}

function readAuthResponseFromUrl(): { [key: string]: string; } {
  // Because of stupid Firefox bug — https://bugzilla.mozilla.org/show_bug.cgi?id=483304
  var location = window.location.toString()
  return parseQueryString(location == null ? null : location.replace(/^[^#]*#?/, ""))
}

interface LoginState {
  network: string
  client_id: string
}

interface OAuthResponse {
  code?: string
  state?: string
  redirect_uri?: string
  proxy_url?: string

  refresh_token?: string

  access_token?: string
  expires_in?: string
  scope?: string
  expires?: number
  error_message?: string
  error_description?: string
  error?: string
}

export interface Session {
  client_id: string

  access_token: string
  expires: number
  scope: string[]

  refresh_token?: string

  isNew?: boolean
}

export class LoginResult {
  constructor(public state: LoginResultState, public session: Session = null) {
  }
}