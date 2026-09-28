(function () {
  const fs = require("fs");
  const path = require("path");

  function resolveProjectFile(relativeToModule) {
    const candidates = [];
    const push = (dir) => {
      candidates.push(path.resolve(dir, relativeToModule));
      candidates.push(path.resolve(dir, "shared", relativeToModule));
    };
    push(process.cwd());
    push(path.resolve(__dirname, ".."));
    let dir = __dirname;
    for (let i = 0; i < 8; i++) {
      push(dir);
      const parent = path.dirname(dir);
      if (parent === dir) break;
      dir = parent;
    }
    for (const candidate of candidates) {
      if (fs.existsSync(candidate)) return candidate;
    }
    throw new Error(
      "Cannot find " + relativeToModule + ". Looked in:\n" + candidates.join("\n")
    );
  }

  config.resolve = config.resolve || {};
  config.resolve.alias = Object.assign({}, config.resolve.alias, {
    "@interfold/describe-js-error-event": resolveProjectFile(
      "src/wasmJsMain/js/describeJsErrorEvent.js"
    ),
    "@interfold/sample-browser-event": resolveProjectFile(
      "src/wasmJsTest/js/sampleBrowserEvent.js"
    ),
  });
})();
