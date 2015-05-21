import {
  login,
  init,
  Credential,
} from "./auth"

// we use cookie only in the development environment
function getCookie(name: string): string {
  var match = document.cookie.match(new RegExp(name + '=([^;]+)'))
  return match == null ? null : decodeURIComponent(match[1])
}

export function check(): Promise<Credential> {
  var userId = getCookie("user")
  if (userId != null) {
    localStorage.setItem("user", userId)
    localStorage.setItem("hello", JSON.stringify({jbHub: {access_token: "42"}}))
    return Promise.resolve(null)
  }

  init(true)

  return login()
}