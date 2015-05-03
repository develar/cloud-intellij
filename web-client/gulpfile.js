var gulp = require("gulp")
var ts = require("gulp-typescript")
var concat = require('gulp-concat')
var uglify = require('gulp-uglify')
var sourcemaps = require('gulp-sourcemaps')
var path = require('path')
var amdOptimize = require("amd-optimize")

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
});

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
var amdOptimizeConf = {
  exclude: excludedModules,
  loader: amdOptimize.loader(function (moduleName) {
    if (excludedModules.indexOf(moduleName) === -1) {
      throw new Error("Unresolved module " + moduleName)
    }
    // https://github.com/scalableminds/amd-optimize/issues/22
    return "lib/empty.js"
  })
}

gulp.task("package", function () {
  return compile()
    .pipe(amdOptimize("fluxPlugin", amdOptimizeConf))
    .pipe(concat("fluxPlugin.js"))
    .pipe(uglify({output: {preamble: '"use strict"'}}))
    .pipe(sourcemaps.write("."))
    .pipe(gulp.dest(outDir + "/dist"))
})

gulp.task('watch', ["compile"], function () {
  gulp.watch(sources, ["compile"])
})

gulp.task('default', ['package'])