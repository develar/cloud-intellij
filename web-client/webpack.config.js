var path = require("path")
var CommonsChunkPlugin = require("webpack/lib/optimize/CommonsChunkPlugin")
// HtmlWebpackPlugin doesn't support CommonsChunkPlugin, so, in the chunks we must specify common chunks explicitly
var HtmlWebpackPlugin = require("html-webpack-plugin")
var ExtractTextPlugin = require("extract-text-webpack-plugin")
var webpack = require("webpack")

var production = process.env.MODE === "production"

var useHash = false

module.exports = {
  entry: {
    ideAuth: "./modules/auth/ideAuth",
    web: "./modules/auth/webAuth",
    fluxPlugin: "./modules/flux-orion-plugin/fluxPlugin",
    pageLinksPlugin: "plugins/pageLinksPlugin.js",
    webEditingPlugin: "plugins/webEditingPlugin.js",
    imageViewerPlugin: "edit/content/imageViewerPlugin.js",
  },
  resolve: {
    extensions: ["", ".ts", ".js"],
    modulesDirectories: [],
    root: [path.resolve("modules"), path.resolve("lib"), path.resolve("lib.d"),
      path.resolve("orion.client/bundles/org.eclipse.orion.client.core/web"),
      path.resolve("orion.client/bundles/org.eclipse.orion.client.editor/web"),
      path.resolve("orion.client/bundles/org.eclipse.orion.client.ui/web"),
    ],
    alias: {
      //"gcli": "gcli/gcli"
      //"i18n": path.resolve("../../orion.client/bundles/org.eclipse.orion.client.core/web/requirejs/i18n.js"),
    }
  },
  resolveLoader: {
    //fallback: path.join(__dirname, "loaders")
    fallback: [path.join(__dirname, "loaders"), path.join(__dirname, "node_modules")]
  },
  plugins: [
    new ExtractTextPlugin("[name].css"),

    new CommonsChunkPlugin("commons.js", ["ideAuth", "web"]),
    new CommonsChunkPlugin("orion-plugin.js", ["fluxPlugin", "pageLinksPlugin", "webEditingPlugin", "imageViewerPlugin"]),
    // move bluebird to common chunk
    new CommonsChunkPlugin("lib.js", ["fluxPlugin", "commons.js"]),

    html("IDE login", "ide-auth.html", ["lib.js", "commons.js", "ideAuth"]),
    html("Editor", "edit.html", ["lib.js", "commons.js", "web"], true),

    html("", "fluxPlugin.html", ["lib.js", "orion-plugin.js", "fluxPlugin"]),
    html("", "pageLinksPlugin.html", ["orion-plugin.js", "pageLinksPlugin"]),
    html("", "webEditingPlugin.html", ["orion-plugin.js", "webEditingPlugin"]),
    html("", "imageViewerPlugin.html", ["orion-plugin.js", "imageViewerPlugin"], true),

    new webpack.DefinePlugin({
      OAUTH_CLIENT_ID_DEV: '"5e190e8e-31c4-462d-b74a-be8025988c8f"',
      OAUTH_CLIENT_ID_PROD: '"0799e9c5-849d-40e8-bbc6-5d5d6c9e711f"',
    }),
  ],
  output: {
    path: production ? "./dist" : "./build",
    filename: useHash ? "[name].[hash].js" : "[name].js",
    chunkFilename: useHash ? "[id].[hash].js" : "[id].js",
  },
  devtool: "source-map",
  module: {
    loaders: [
      {
        test: /\.ts$/,
        loader: "awesome-typescript-loader",
        query: {noImplicitAny: true},
      },
      {
        test: /\.css$/,
        loader: ExtractTextPlugin.extract("style-loader", "css-loader")
      },
      {
        test: /\.(png|woff|woff2|eot|ttf|svg)$/,
        loader: "url-loader?limit=100000"
      },
    ],
    noParse: [
      path.join(__dirname, "lib", "bluebird"),
    ],
  },
}

function html(title, filename, chunks, customTemplate) {
  return new HtmlWebpackPlugin({
    title: title,
    filename: filename,
    chunks: chunks,
    template: "modules/resources/" + (customTemplate ? filename : "page-template.html")
  })
}