module.exports = function (content) {
}
module.exports.pitch = function (remainingRequest, precedingRequest, data) {
  return "module.exports = require(" + JSON.stringify("-!" + remainingRequest.replace("nls/", "nls/root/")) + ");";
}