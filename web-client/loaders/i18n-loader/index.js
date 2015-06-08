module.exports = function () {
}
module.exports.pitch = function (remainingRequest) {
  this.cacheable()
  return "module.exports = require(" + JSON.stringify("-!" + remainingRequest.replace("nls/", "nls/root/")) + ");"
}