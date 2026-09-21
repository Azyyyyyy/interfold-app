const fs = require("fs");
const path = require("path");

function readUrlFile(filePath) {
  try {
    if (fs.existsSync(filePath)) {
      return fs.readFileSync(filePath, "utf8").trim();
    }
  } catch (_e) {
    // ignore
  }
  return "";
}

function readReady(name) {
  return (
    process.env["INTERFOLD_" + name] ||
    readUrlFile(path.resolve(__dirname, name.toLowerCase().split("_").join("-") + ".txt")) ||
    readUrlFile(process.env["INTERFOLD_" + name + "_FILE"] || "") ||
    readUrlFile(path.resolve(process.cwd(), "build/wasm-it/" + name.toLowerCase().split("_").join("-") + ".txt")) ||
    readUrlFile(path.resolve(__dirname, "../../../../shared/build/wasm-it/" + name.toLowerCase().split("_").join("-") + ".txt")) ||
    ""
  );
}

// Karma inlines this file into `build/wasm/packages/<pkg>/karma.conf.js`.
// Gradle copies the ready-files next to that conf as backend-*.txt.
const backendUrl = process.env.INTERFOLD_BACKEND_URL || readReady("BACKEND_URL");
const backendToken = process.env.INTERFOLD_BACKEND_TOKEN || readReady("BACKEND_TOKEN");
const backendSystemId = process.env.INTERFOLD_BACKEND_SYSTEM_ID || readReady("BACKEND_SYSTEM_ID");
const requireBackend = !!(backendUrl || process.env.INTERFOLD_REQUIRE_BACKEND);

const webpack = require("webpack");
config.plugins.push(
  new webpack.DefinePlugin({
    "globalThis.__INTERFOLD_BACKEND_URL__": JSON.stringify(backendUrl),
    "globalThis.__INTERFOLD_BACKEND_TOKEN__": JSON.stringify(backendToken),
    "globalThis.__INTERFOLD_BACKEND_SYSTEM_ID__": JSON.stringify(backendSystemId),
    "globalThis.__INTERFOLD_REQUIRE_BACKEND__": JSON.stringify(requireBackend),
  })
);
