var path = require("path")
var CommonsChunkPlugin = require("webpack/lib/optimize/CommonsChunkPlugin")
// HtmlWebpackPlugin doesn't support CommonsChunkPlugin, so, in the chunks we must specify common chunks explicitly
var HtmlWebpackPlugin = require("html-webpack-plugin")

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
    new HtmlWebpackPlugin({
      title: "IDE login",
      filename: "ide-auth.html",
      chunks: ["auth.js", "ideAuth"],
      minify: true
    }),
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
    noParse: [
      path.join(__dirname, "lib", "bluebird"),
    ],
  },
}