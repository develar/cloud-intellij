var gulp = require('gulp')
var ts = require('gulp-typescript')
var concat = require('gulp-concat')
var uglify = require('gulp-uglify')
var newer = require('gulp-newer')
var sourcemaps = require('gulp-sourcemaps')
var path = require('path')

var outDir = 'out'
var sources = "src/**/*.ts";

var tsProject = ts.createProject({
  target: "ES5",
  noImplicitAny: true,
  removeComments: true,
  sortOutput: true,
  noExternalResolve: true,
  noEmitOnError: true,
  module: "amd",
  typescript: require('typescript')
});

gulp.task("compile", function () {
  var tsResult = gulp.src([sources, "lib.d/**/*.d.ts"])
      .pipe(sourcemaps.init())
      //.pipe(newer(outDir + '/' + outFile))
      .pipe(ts(tsProject));

  tsResult.js
      //.pipe(concat(outFile))
      //.pipe(uglify({
      //        output: {
      //          beautify: true,
      //          indent_level: 2
      //        }
      //      }))
      .pipe(sourcemaps.write('.', {includeContent: true, sourceRoot: path.resolve('src')}))
      .pipe(gulp.dest(outDir))
})

gulp.task("package", ['compile'], function () {
  var amdOptimize = require("amd-optimize")
  gulp.src(["out/*.js"])
      .pipe(amdOptimize("fluxPlugin", {
              configFile: "out/requireConfig.js",
              exclude: ["Deferred", "sockjs", "stomp", "bluebird", "sha1", "orion/plugin"],
              loader: amdOptimize.loader(function (moduleName) {
                return "lib/empty.js"
              })
            }))
      .pipe(sourcemaps.init({loadMaps: true}))
      .pipe(concat("fluxPlugin.js"))
      .pipe(sourcemaps.write('.', {includeContent: true, sourceRoot: path.resolve('src')}))
      .pipe(gulp.dest(outDir + "/dist"))
})

gulp.task('watch', ['compile'], function () {
  gulp.watch([sources, "lib.d/**/*.d.ts"], ['compile']);
});

gulp.task('default', ['compile']);