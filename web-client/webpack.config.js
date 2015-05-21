var path = require("path")
var CommonsChunkPlugin = require("webpack/lib/optimize/CommonsChunkPlugin")
// HtmlWebpackPlugin doesn't support CommonsChunkPlugin, so, in the chunks we must specify common chunks explicitly
var HtmlWebpackPlugin = require("html-webpack-plugin")
var webpack = require("webpack")

var production = process.env.MODE === "production"

module.exports = {
  resolve: {
    extensions: ["", ".ts", ".js"],
    modulesDirectories: [],
    root: [path.resolve("modules"), path.resolve("lib"), path.resolve("lib.d")]
  },
  entry: {
    ideAuth: "./modules/auth/ideAuth",
    webAuth: "./modules/auth/webAuth",
    fluxPlugin: "./modules/flux-orion-plugin/fluxPlugin",
  },
  plugins: [
    new CommonsChunkPlugin("auth.js", ["ideAuth", "webAuth"]),
    new HtmlWebpackPlugin({
      title: "IDE login",
      filename: "ide-auth.html",
      chunks: ["auth.js", "ideAuth"],
      minify: true
    }),
    new webpack.DefinePlugin({
      OAUTH_CLIENT_ID_DEV: '"5e190e8e-31c4-462d-b74a-be8025988c8f"',
      OAUTH_CLIENT_ID_PROD: '"0799e9c5-849d-40e8-bbc6-5d5d6c9e711f"',
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