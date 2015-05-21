var gulp = require("gulp")
var ts = require("gulp-typescript")
var sourcemaps = require('gulp-sourcemaps')
var path = require('path')
var outDir = "out"
var sources = ["src/**/*.ts", "lib.d/**/*.d.ts"]

var tsProject = ts.createProject({
  target: "ES5",
  noImplicitAny: true,
  removeComments: true,
  sortOutput: true,
  noExternalResolve: true,
  noEmitOnError: true,
  module: "amd",
  typescript: require("typescript")
})

function compile() {
  var tsResult = gulp.src(sources)
    .pipe(sourcemaps.init())
    .pipe(ts(tsProject))
  return tsResult.js
}

gulp.task("compile", function () {
  return compile()
    .pipe(sourcemaps.write("."))
    .pipe(gulp.dest(outDir + "/files"))
})

var excludedModules = ["Deferred", "stomp", "bluebird", "sha1", "hello", "orion/plugin", "orion/EventTarget"]

gulp.task("watch", ["compile"], function () {
  gulp.watch(sources, ["compile"])
})

gulp.task('default', ["compile"])