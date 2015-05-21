var path = require("path")
var CommonsChunkPlugin = require("webpack/lib/optimize/CommonsChunkPlugin")

var production = process.env.MODE === "production"

module.exports = {
  resolve: {
    extensions: ["", ".ts", ".js"],
    modulesDirectories: [],
    root: [__dirname, path.resolve("lib"), path.resolve("lib.d")]
  },
  entry: {
    ideAuth: "./auth/ideAuth",
    webAuth: "./auth/webAuth",
    fluxPlugin: "./src/fluxPlugin",
  },
  plugins: [
    new CommonsChunkPlugin("auth.js", ["ideAuth", "webAuth"]),
  ],
  output: {
    path: production ? "./dist" : "./build",
    filename: "[name].js",
    chunkFilename: "[id].js",
  },
  devtool: "source-map",
  module: {
    loaders: [
      {
        test: /\.ts$/,
        loader: "awesome-typescript-loader",
        query: {noImplicitAny: true},
      },
    ],
    postLoaders: [
      {
        test: /webAuth/,
        loader: "expose?Auth",
      },
    ],
    //noParse: [
    //  path.join(__dirname, "lib", "sha1"),
    //],
  },
}