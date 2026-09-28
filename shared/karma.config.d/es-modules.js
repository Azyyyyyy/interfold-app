// Webpack 5.108 (Kotlin 2.4.20) leaves `import.meta` in vendor chunks (jose).
// karma-webpack always splits `runtime.js` / `commons.js` and includes them as
// classic scripts. Load them as ES modules — same contract as webApp/index.html.
function isWebpackJs(pattern) {
  if (!pattern) return false;
  var p = String(pattern).replace(/\\/g, "/");
  if (/\.mjs$/i.test(p)) return true;
  if (p.indexOf("_karma_webpack_") !== -1 && /\.js$/i.test(p)) return true;
  if (/(^|\/)(commons|runtime)\.js$/i.test(p)) return true;
  return false;
}

function markWebpackJsAsModules(files) {
  if (!files) return;
  for (var i = 0; i < files.length; i++) {
    var entry = files[i];
    if (typeof entry === "string") {
      if (isWebpackJs(entry)) {
        files[i] = { pattern: entry, type: "module" };
      }
      continue;
    }
    if (!entry || !entry.pattern || entry.type === "module") continue;
    if (isWebpackJs(entry.pattern)) {
      entry.type = "module";
    }
  }
}

function InterfoldEsModulesFramework(config) {
  markWebpackJsAsModules(config.files);
}
InterfoldEsModulesFramework.$inject = ["config"];

config.plugins = config.plugins || [];
config.plugins.push({
  "framework:interfold-es-modules": ["factory", InterfoldEsModulesFramework],
});
// After karma-webpack, which unshifts runtime.js / commons.js.
config.frameworks = (config.frameworks || []).concat("interfold-es-modules");

if (config.webpack) {
  config.webpack.output = config.webpack.output || {};
  config.webpack.output.scriptType = "module";
}
