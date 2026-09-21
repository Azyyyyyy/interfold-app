const fs = require("fs");
const path = require("path");

// Inlined into karma.conf.js, so __dirname is build/wasm/packages/<pkg>.
// Only Gradle env counts as "injected" — leftover wasm-it files must not
// make :shared:wasmJsBrowserTest look like an integration run.
const backendUrl = process.env.INTERFOLD_BACKEND_URL || "";
const requireBackend = !!process.env.INTERFOLD_REQUIRE_BACKEND;

const hookFile = path.join(__dirname, "skip-backend-tests-hook.js");
fs.writeFileSync(
  hookFile,
  [
    "(function (backendUrl, requireBackend) {",
    "  if (backendUrl || requireBackend) return;",
    "  var skip = {",
    "    loadClient_completesInit_overInMemoryBackend: true,",
    "    createAlter_emitsAlterCreatedEvent: true",
    "  };",
    "  function wrap(origIt) {",
    "    function wrapped(name, fn) {",
    "      if (skip[name]) {",
    "        return origIt(name, function () { this.skip(); });",
    "      }",
    "      return origIt.apply(this, arguments);",
    "    }",
    "    wrapped.__interfoldSkipWrapped = true;",
    "    return wrapped;",
    "  }",
    "  function install(value) {",
    "    if (typeof value !== 'function' || value.__interfoldSkipWrapped) return value;",
    "    return wrap(value);",
    "  }",
    "  var current = globalThis.it;",
    "  Object.defineProperty(globalThis, 'it', {",
    "    configurable: true,",
    "    enumerable: true,",
    "    get: function () { return current; },",
    "    set: function (value) { current = install(value); }",
    "  });",
    "  if (typeof current === 'function') current = install(current);",
    "})(" + JSON.stringify(backendUrl) + ", " + JSON.stringify(!!requireBackend) + ");",
    "",
  ].join("\n")
);

config.files = config.files || [];
var hookEntry = {
  pattern: hookFile.replace(/\\/g, "/"),
  included: true,
  served: true,
  watched: false,
};
var inserted = false;
for (var i = 0; i < config.files.length; i++) {
  var entry = config.files[i];
  var pattern = typeof entry === "string" ? entry : entry && entry.pattern;
  if (pattern && String(pattern).replace(/\\/g, "/").endsWith("/load.mjs")) {
    config.files.splice(i, 0, hookEntry);
    inserted = true;
    break;
  }
}
if (!inserted) {
  config.files.unshift(hookEntry);
}
