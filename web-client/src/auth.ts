import oauth = require("hello")

// we use cookie only in the development environment
function getCookie(name: string): string {
  var match = document.cookie.match(new RegExp(name + '=([^;]+)'))
  return match == null ? null : decodeURIComponent(match[1])
}

function endsWith(str: string, suffix: string): boolean {
  return str.indexOf(suffix, str.length - suffix.length) !== -1
}

export function init(pageUri: string) {
  oauth.init({
      jbHub: endsWith(location.host, ".dev") ? "e36c2ef7-735d-4ae1-a0ec-71ea19052e0a" : "0799e9c5-849d-40e8-bbc6-5d5d6c9e711f"
    },
    {
      response_type: "code",
      oauth_proxy: "http://oauth-shim.intellij-io.develar.svc.tutum.io:3000",
      redirect_uri: "/auth/redirect.html",
      page_uri: pageUri,
      display: "page"
    })
}

export interface Credential {
  id: string
  token: string
}

export function login(): Promise<Credential> {
  var session = oauth.getAuthResponse("jbHub")
  var id = localStorage.getItem("user")
  if (id != null && session != null && "access_token" in session && session.access_token != null && "expires" in session && session.expires > (Date.now() / 1000)) {
    return Promise.resolve({id: id, token: session.access_token})
  }

  var provider = oauth.use("jbHub")
  return provider.login({force: false})
    .then((result) => {
      var token = result.authResponse.access_token
      // if session expires, we update user id in any case, to avoid stale data (user can log in using different credentials)
      return provider.api("/me?fields=id")
        .then((result) => {
          var id = result.id
          localStorage.setItem("user", id)
          return Promise.resolve({id: id, token: token})
        })
    })
}

export function check(): Promise<any> {
  var userId = getCookie("user")
  if (userId != null) {
    localStorage.setItem("user", userId)
    localStorage.setItem("hello", JSON.stringify({jbHub: {access_token: "42"}}))
    return Promise.resolve()
  }
  else {
    return login()
  }
}