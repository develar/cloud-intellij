var path = require("path")
var CommonsChunkPlugin = require("webpack/lib/optimize/CommonsChunkPlugin")
// HtmlWebpackPlugin doesn't support CommonsChunkPlugin, so, in the chunks we must specify common chunks explicitly
var HtmlWebpackPlugin = require("html-webpack-plugin")
var ExtractTextPlugin = require("extract-text-webpack-plugin")
var webpack = require("webpack")
var Clean = require("clean-webpack-plugin")

const production = process.env.MODE === "production"
const outDir = production ? "dist" : "build"
const useHash = production

function abs(relativePath) {
  return path.join(__dirname, relativePath)
}

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
    root: [
      abs("modules"),
      abs("lib"),
      abs("lib.d"),
      abs("orion.client/bundles/org.eclipse.orion.client.core/web"),
      abs("orion.client/bundles/org.eclipse.orion.client.editor/web"),
      abs("orion.client/bundles/org.eclipse.orion.client.ui/web"),
    ],
  },
  resolveLoader: {
    fallback: [abs("loaders"), abs("node_modules")],
  },
  plugins: [
    new Clean(production ? [outDir]: []),

    new ExtractTextPlugin(useHash ? "[name].[contenthash].css" : "[name].css"),

    new CommonsChunkPlugin({name: "commons", minChunks: 2}),

    html("IDE login", "ide-auth.html", ["commons", "ideAuth"]),
    html("Editor", "edit.html", ["commons", "web"], true),

    html("", "fluxPlugin.html", ["commons", "fluxPlugin"]),
    html("", "pageLinksPlugin.html", ["commons", "pageLinksPlugin"]),
    html("", "webEditingPlugin.html", ["commons", "webEditingPlugin"]),
    html("", "imageViewerPlugin.html", ["commons", "imageViewerPlugin"], true),

    new webpack.DefinePlugin({
      OAUTH_CLIENT_ID_DEV: '"d5d27f53-31b3-493f-ac9e-2c48da5661ea"',
      OAUTH_CLIENT_ID_PROD: '"0799e9c5-849d-40e8-bbc6-5d5d6c9e711f"',
    }),
  ],
  output: {
    path: "./" + outDir,
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
  devServer: {
    contentBase: "./build",
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