import {
  login,
  init,
  Credential,
  } from "auth"

function getParameterByName(name: string) {
  var match = new RegExp("[?&]" + name + "=([^&]*)").exec(window.location.search)
  return match == null ? null : decodeURIComponent(match[1].replace(/\+/g, " "))
}

var requestId = getParameterByName("r")
if (requestId != null) {
  sessionStorage.setItem("requestId", requestId)
  sessionStorage.setItem("port", getParameterByName("port"))
}

init("/ide.html")

login()
  .then((credential) => {
    var request = new XMLHttpRequest()
    // only IP instead of name (localhost, for example) must be used due to obvious security reasons
    request.open("post", "http://127.0.0.1:" + sessionStorage.getItem("port") + "/67822818-87E4-4FF9-81C5-75433D57E7B3")
    request.send(JSON.stringify([sessionStorage.getItem("requestId"), credential.id, credential.token]))
    request.onload = function () {
      if (request.status === 200 || request.status === 0) {
        window.close()
      }
      else {
        throw new Error(request.responseText)
      }
    };
  })