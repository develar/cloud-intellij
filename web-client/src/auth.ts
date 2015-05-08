import oauth = require("hello")

// we use cookie only in the development environment
function getCookie(name: string): string {
  var match = document.cookie.match(new RegExp(name + '=([^;]+)'))
  return match == null ? null : decodeURIComponent(match[1])
}

var userId = getCookie("user")
if (userId != null) {
  localStorage.setItem("user", userId)
  localStorage.setItem("hello", JSON.stringify({jbHub: {access_token: "42"}}))
}
else {
  oauth.init({
      jbHub: "e36c2ef7-735d-4ae1-a0ec-71ea19052e0a"
    },
    {
      redirect_uri: "/auth/redirect.html",
      page_uri: "/edit/edit.html",
      display: "page"
    })

  var provider = oauth.use("jbHub")
  provider.login({force: false})
    .then(() => {
      var id = localStorage.getItem("user")
      if (id != null) {
        return id
      }

      return provider.api("/me?fields=id")
        .then((result) => {
          id = result.id
          localStorage.setItem("user", id)
          return id
        })
    })
}