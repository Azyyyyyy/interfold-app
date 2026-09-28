const pwaE2ePath = require("path");
const pwaE2eFs = require("fs");

// Inlined into karma.conf.js, so __dirname is build/wasm/packages/<pkg>.
const pwaE2eFixtureDir = pwaE2ePath.resolve(__dirname, "../../../pwa-e2e");
const pwaE2eSwFile = pwaE2ePath.join(pwaE2eFixtureDir, "service-worker.js");
const pwaE2eSwJsFile = pwaE2ePath.join(pwaE2eFixtureDir, "interfold-sw.js");

const pwaE2eHelperFile = pwaE2ePath.join(__dirname, "pwa-e2e-helpers-hook.js");
pwaE2eFs.writeFileSync(
  pwaE2eHelperFile,
  [
    "(function () {",
    "  window.__interfoldPwaE2EReset = async function () {",
    "    if (!('serviceWorker' in navigator) || !('caches' in window)) {",
    "      throw new Error('service worker or caches unavailable');",
    "    }",
    "    var regs = await navigator.serviceWorker.getRegistrations();",
    "    await Promise.all(regs.map(function (reg) { return reg.unregister(); }));",
    "    var keys = await caches.keys();",
    "    await Promise.all(keys.filter(function (key) {",
    "      return key.indexOf('interfold-') === 0;",
    "    }).map(function (key) { return caches.delete(key); }));",
    "  };",
    "  window.__interfoldPwaE2ESeed = async function () {",
    "    var oldName = 'interfold-app-cache-e2e-old';",
    "    var newName = 'interfold-app-cache-e2e-incoming';",
    "    var oldCache = await caches.open(oldName);",
    "    await oldCache.put('/interfold-app.js', new Response('OLD_JS', { headers: { 'content-type': 'text/javascript' } }));",
    "    await oldCache.put('/index.html', new Response('<html>old</html>', { headers: { 'content-type': 'text/html' } }));",
    "    var newCache = await caches.open(newName);",
    "    await newCache.put('/interfold-app.js', new Response('NEW_JS', { headers: { 'content-type': 'text/javascript' } }));",
    "    await newCache.put('/index.html', new Response('<html>new</html>', { headers: { 'content-type': 'text/html' } }));",
    "    var meta = await caches.open('interfold-sw-meta-v1');",
    "    await meta.put('/__serving-cache-name', new Response(oldName, { headers: { 'content-type': 'text/plain' } }));",
    "  };",
    "  window.__interfoldPwaE2ERegister = async function () {",
    "    await navigator.serviceWorker.register('/service-worker.js', { scope: '/' });",
    "    await navigator.serviceWorker.ready;",
    "    if (navigator.serviceWorker.controller) return;",
    "    await new Promise(function (resolve, reject) {",
    "      var timer = setTimeout(function () { reject(new Error('controllerchange timeout')); }, 10000);",
    "      navigator.serviceWorker.addEventListener('controllerchange', function () {",
    "        clearTimeout(timer);",
    "        resolve();",
    "      }, { once: true });",
    "    });",
    "  };",
    "})();",
    "",
  ].join("\n")
);

config.files = config.files || [];
config.files.unshift({
  pattern: pwaE2eHelperFile.replace(/\\/g, "/"),
  included: true,
  served: true,
  watched: false,
});

if (pwaE2eFs.existsSync(pwaE2eSwFile) && pwaE2eFs.existsSync(pwaE2eSwJsFile)) {
  config.files = config.files || [];
  config.files.push(
    { pattern: pwaE2eSwFile.replace(/\\/g, "/"), included: false, served: true, watched: false },
    { pattern: pwaE2eSwJsFile.replace(/\\/g, "/"), included: false, served: true, watched: false },
  );
  const pwaE2eToAbsolute = (filePath) => "/absolute" + filePath.replace(/\\/g, "/");
  config.proxies = Object.assign({}, config.proxies, {
    "/service-worker.js": pwaE2eToAbsolute(pwaE2eSwFile),
    "/interfold-sw.js": pwaE2eToAbsolute(pwaE2eSwJsFile),
  });
  config.customHeaders = (config.customHeaders || []).concat([
    { match: "service-worker\\.js$", name: "Service-Worker-Allowed", value: "/" },
    { match: "service-worker\\.js$", name: "Cache-Control", value: "no-cache" },
    { match: "interfold-sw\\.js$", name: "Cache-Control", value: "no-cache" },
  ]);
}
