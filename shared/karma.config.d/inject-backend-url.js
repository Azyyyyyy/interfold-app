const injectFs = require("fs");
const injectPath = require("path");

// Webpack DefinePlugin does not rewrite Kotlin/Wasm @JsFun snippets, so the
// last IT "pass" was a 0s no-op. Assign the ready-file values on globalThis
// at page load (this hook is included before load.mjs).
function readInjectSibling(name) {
  try {
    const p = injectPath.join(__dirname, name);
    if (injectFs.existsSync(p)) return injectFs.readFileSync(p, "utf8").trim();
  } catch (_e) {}
  return "";
}

const injectBackendUrl = process.env.INTERFOLD_BACKEND_URL || readInjectSibling("backend-url.txt");
const injectBackendToken = process.env.INTERFOLD_BACKEND_TOKEN || readInjectSibling("backend-token.txt");
const injectBackendSystemId =
  process.env.INTERFOLD_BACKEND_SYSTEM_ID || readInjectSibling("backend-system-id.txt");
const injectBackendTokenA =
  process.env.INTERFOLD_BACKEND_TOKEN_A || readInjectSibling("backend-token-a.txt");
const injectBackendSystemIdA =
  process.env.INTERFOLD_BACKEND_SYSTEM_ID_A || readInjectSibling("backend-system-id-a.txt");
const injectRequireBackend = !!(injectBackendUrl || process.env.INTERFOLD_REQUIRE_BACKEND);

const injectHookFile = injectPath.join(__dirname, "inject-backend-url-hook.js");
injectFs.writeFileSync(
  injectHookFile,
  [
    "(function () {",
    "  globalThis.__INTERFOLD_BACKEND_URL__ = " + JSON.stringify(injectBackendUrl) + ";",
    "  globalThis.__INTERFOLD_BACKEND_TOKEN__ = " + JSON.stringify(injectBackendToken) + ";",
    "  globalThis.__INTERFOLD_BACKEND_SYSTEM_ID__ = " + JSON.stringify(injectBackendSystemId) + ";",
    "  globalThis.__INTERFOLD_BACKEND_TOKEN_A__ = " + JSON.stringify(injectBackendTokenA) + ";",
    "  globalThis.__INTERFOLD_BACKEND_SYSTEM_ID_A__ = " + JSON.stringify(injectBackendSystemIdA) + ";",
    "  globalThis.__INTERFOLD_REQUIRE_BACKEND__ = " + JSON.stringify(injectRequireBackend) + ";",
    "})();",
    "",
  ].join("\n")
);

config.files = config.files || [];
var injectHookEntry = {
  pattern: injectHookFile.replace(/\\/g, "/"),
  included: true,
  served: true,
  watched: false,
};
var injectInserted = false;
for (var injectIdx = 0; injectIdx < config.files.length; injectIdx++) {
  var injectEntry = config.files[injectIdx];
  var injectPattern = typeof injectEntry === "string" ? injectEntry : injectEntry && injectEntry.pattern;
  if (injectPattern && String(injectPattern).replace(/\\/g, "/").endsWith("/load.mjs")) {
    config.files.splice(injectIdx, 0, injectHookEntry);
    injectInserted = true;
    break;
  }
}
if (!injectInserted) {
  config.files.unshift(injectHookEntry);
}
