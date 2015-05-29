module.exports = function (content) {
  this.cacheable()
  this.value = content
  return "module.exports = " + JSON.stringify(content)
}