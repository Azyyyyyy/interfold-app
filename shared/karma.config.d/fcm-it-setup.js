const fcmItFs = require("fs");
const fcmItPath = require("path");
const fcmItHttp = require("http");
const fcmItOs = require("os");

function fcmItReadSibling(name) {
  try {
    const p = fcmItPath.join(__dirname, name);
    if (fcmItFs.existsSync(p)) return fcmItFs.readFileSync(p, "utf8").trim();
  } catch (_e) {}
  return "";
}

var interfoldFcmIt = !!(
  process.env.INTERFOLD_IT_FCM_ENABLED ||
  (fcmItFs.existsSync(fcmItPath.join(__dirname, "backend-fcm.txt")) &&
    fcmItFs.readFileSync(fcmItPath.join(__dirname, "backend-fcm.txt"), "utf8").trim() === "1")
);
var interfoldFcmBackendUrl =
  process.env.INTERFOLD_BACKEND_URL || fcmItReadSibling("backend-url.txt");
// Unique per Karma process so a leftover Chrome cannot steal 9333 or reuse a
// profile that already auto-denied notifications.
var interfoldCdpPort = 9333 + (process.pid % 500);
var interfoldFcmUserDataDir = fcmItPath.join(
  fcmItOs.tmpdir(),
  "interfold-karma-chrome-fcm-" + process.pid
);

function fcmItCdpPort() {
  try {
    var active = fcmItPath.join(interfoldFcmUserDataDir, "DevToolsActivePort");
    if (fcmItFs.existsSync(active)) {
      var n = parseInt(fcmItFs.readFileSync(active, "utf8").split(/\r?\n/)[0], 10);
      if (n > 0) return n;
    }
  } catch (_e) {}
  return interfoldCdpPort;
}

function fcmItGetJson(url) {
  return new Promise(function (resolve, reject) {
    fcmItHttp
      .get(url, function (res) {
        var body = "";
        res.on("data", function (c) { body += c; });
        res.on("end", function () {
          if (res.statusCode && res.statusCode >= 400) {
            reject(new Error(url + " -> " + res.statusCode + " " + body));
            return;
          }
          try {
            resolve(JSON.parse(body));
          } catch (e) {
            reject(new Error(url + " returned non-JSON: " + body.slice(0, 200)));
          }
        });
      })
      .on("error", reject);
  });
}

function fcmItOpenCdp(wsUrl) {
  return new Promise(function (resolve, reject) {
    var socket;
    var usingWs = false;
    try {
      socket = new (require("ws"))(wsUrl);
      usingWs = true;
    } catch (_e) {
      if (typeof WebSocket !== "function") {
        reject(new Error("CDP grant needs the 'ws' package or Node WebSocket"));
        return;
      }
      socket = new WebSocket(wsUrl);
    }
    var pending = {};
    var nextId = 1;
    var opened = false;

    function onOpen() {
      if (opened) return;
      opened = true;
      resolve({
        send: function (method, params) {
          return new Promise(function (res, rej) {
            var id = nextId++;
            pending[id] = { res: res, rej: rej };
            socket.send(JSON.stringify({ id: id, method: method, params: params || {} }));
          });
        },
        close: function () {
          try { socket.close(); } catch (_e) {}
        },
      });
    }

    function onMessage(data) {
      var raw = data;
      if (data && typeof data === "object" && data.data != null) raw = data.data;
      if (typeof Buffer !== "undefined" && Buffer.isBuffer && Buffer.isBuffer(raw)) {
        raw = raw.toString("utf8");
      }
      if (typeof raw !== "string") {
        try { raw = String(raw); } catch (_e) { return; }
      }
      var msg;
      try { msg = JSON.parse(raw); } catch (_e) { return; }
      if (!msg || msg.id == null || !pending[msg.id]) return;
      var waiter = pending[msg.id];
      delete pending[msg.id];
      if (msg.error) {
        waiter.rej(new Error(msg.error.message || JSON.stringify(msg.error)));
      } else {
        waiter.res(msg.result);
      }
    }

    function onError(err) {
      if (!opened) reject(err && err.message ? err : new Error(String(err)));
    }

    if (usingWs) {
      socket.on("open", onOpen);
      socket.on("message", onMessage);
      socket.on("error", onError);
    } else {
      socket.addEventListener("open", onOpen);
      socket.addEventListener("message", onMessage);
      socket.addEventListener("error", function (ev) { onError(ev.error || ev); });
    }
  });
}

function fcmItGrantOrigins(requestedOrigin) {
  var port = config.port || 9876;
  var origins = [
    "http://localhost:" + port,
    "http://127.0.0.1:" + port,
  ];
  if (requestedOrigin && origins.indexOf(requestedOrigin) === -1) {
    origins.push(requestedOrigin);
  }
  return origins;
}

function fcmItCdpGrant(requestedOrigin) {
  var origins = fcmItGrantOrigins(requestedOrigin);
  var attempts = 0;
  function tryOnce() {
    var port = fcmItCdpPort();
    var summary = { port: port, origins: origins, contexts: [], pages: 0, emulation: [] };
    return fcmItGetJson("http://127.0.0.1:" + port + "/json/version")
      .then(function (info) {
        if (!info || !info.webSocketDebuggerUrl) {
          throw new Error("CDP /json/version had no webSocketDebuggerUrl");
        }
        return fcmItOpenCdp(info.webSocketDebuggerUrl).then(function (cdp) {
          var permissions = ["notifications"];
          return cdp
            .send("Target.getTargets")
            .catch(function () { return { targetInfos: [] }; })
            .then(function (result) {
              var targets = (result && result.targetInfos) || [];
              var contextIds = [];
              targets.forEach(function (t) {
                if (t.browserContextId && contextIds.indexOf(t.browserContextId) === -1) {
                  contextIds.push(t.browserContextId);
                }
              });
              summary.contexts = contextIds;
              var chain = cdp.send("Browser.grantPermissions", { permissions: permissions });
              origins.forEach(function (origin) {
                chain = chain.then(function () {
                  return cdp.send("Browser.grantPermissions", {
                    origin: origin,
                    permissions: permissions,
                  });
                });
                contextIds.forEach(function (ctx) {
                  chain = chain.then(function () {
                    return cdp.send("Browser.grantPermissions", {
                      origin: origin,
                      permissions: permissions,
                      browserContextId: ctx,
                    });
                  });
                });
              });
              return chain;
            })
            .then(function () { cdp.close(); return summary; })
            .catch(function (err) {
              try { cdp.close(); } catch (_e) {}
              throw err;
            });
        });
      })
      .then(function () {
        return fcmItGetJson("http://127.0.0.1:" + port + "/json/list").catch(function () {
          return [];
        });
      })
      .then(function (targets) {
        var pages = (Array.isArray(targets) ? targets : []).filter(function (t) {
          return t && t.type === "page" && t.webSocketDebuggerUrl;
        });
        summary.pages = pages.length;
        var chain = Promise.resolve();
        pages.forEach(function (page) {
          chain = chain.then(function () {
            return fcmItOpenCdp(page.webSocketDebuggerUrl).then(function (cdp) {
              var inner = Promise.resolve();
              origins.forEach(function (origin) {
                inner = inner.then(function () {
                  return cdp
                    .send("Emulation.setPermission", {
                      permission: { name: "notifications" },
                      origin: origin,
                      setting: "granted",
                    })
                    .then(function () { summary.emulation.push("ok:" + origin); })
                    .catch(function (err) {
                      summary.emulation.push("err:" + origin + ":" + (err && err.message ? err.message : err));
                    });
                });
              });
              return inner
                .then(function () { cdp.close(); })
                .catch(function (err) {
                  try { cdp.close(); } catch (_e) {}
                  throw err;
                });
            });
          });
        });
        return chain.then(function () { return summary; });
      });
  }
  function retry(err) {
    attempts += 1;
    if (attempts > 40) {
      return Promise.reject(err || new Error("CDP grant timed out on port " + fcmItCdpPort()));
    }
    return new Promise(function (resolve) { setTimeout(resolve, 250); }).then(tryOnce).catch(retry);
  }
  return tryOnce().catch(retry);
}

if (interfoldFcmIt) {
  var headed =
    process.env.INTERFOLD_CHROME_HEADED === "1" || fcmItReadSibling("chrome-headed.txt") === "1";
  fcmItFs.mkdirSync(fcmItPath.join(interfoldFcmUserDataDir, "Default"), { recursive: true });
  var fcmItPort = config.port || 9876;
  fcmItFs.writeFileSync(
    fcmItPath.join(interfoldFcmUserDataDir, "Default", "Preferences"),
    JSON.stringify({
      profile: {
        default_content_setting_values: { notifications: 1 },
        content_settings: {
          exceptions: {
            notifications: {
              "http://localhost:*,*": { setting: 1, last_modified: "13300000000000000" },
              "http://127.0.0.1:*,*": { setting: 1, last_modified: "13300000000000000" },
            },
          },
        },
      },
    })
  );
  try {
    var fcmPrefs = JSON.parse(
      fcmItFs.readFileSync(fcmItPath.join(interfoldFcmUserDataDir, "Default", "Preferences"), "utf8")
    );
    fcmPrefs.profile.content_settings.exceptions.notifications["http://localhost:" + fcmItPort + ",*"] = {
      setting: 1,
      last_modified: "13300000000000000",
    };
    fcmPrefs.profile.content_settings.exceptions.notifications["http://127.0.0.1:" + fcmItPort + ",*"] = {
      setting: 1,
      last_modified: "13300000000000000",
    };
    fcmItFs.writeFileSync(
      fcmItPath.join(interfoldFcmUserDataDir, "Default", "Preferences"),
      JSON.stringify(fcmPrefs)
    );
  } catch (_e) {}

  // chromeDataDir (not a second --user-data-dir flag): karma-chrome-launcher
  // already prepends its own profile dir, which previously hid these prefs.
  var fcmItFlags = [
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-popup-blocking",
    "--remote-debugging-port=" + interfoldCdpPort,
    "--remote-debugging-address=127.0.0.1",
  ];
  if (!headed) {
    fcmItFlags.push("--headless=new", "--disable-gpu");
  }
  if (process.env.CI) {
    fcmItFlags.push("--no-sandbox", "--disable-dev-shm-usage");
  }
  config.customLaunchers = Object.assign({}, config.customLaunchers, {
    ChromeFcmIt: {
      base: "Chrome",
      chromeDataDir: interfoldFcmUserDataDir,
      flags: fcmItFlags,
    },
  });
  config.browsers = ["ChromeFcmIt"];
  config.customHeaders = (config.customHeaders || []).concat([
    { match: ".*", name: "Permissions-Policy", value: "notifications=*" },
  ]);

  var fcmProdSwSrc = fcmItPath.resolve(__dirname, "../../../../webApp/src/wasmJsMain/resources/service-worker.js");
  var fcmProdSwDest = fcmItPath.join(__dirname, "production-service-worker.js");
  var fcmSwStubDest = fcmItPath.join(__dirname, "fcm-interfold-sw-stub.js");
  fcmItFs.writeFileSync(
    fcmSwStubDest,
    "self.InterfoldServiceWorker = { start: function () {} };\n"
  );
  if (fcmItFs.existsSync(fcmProdSwSrc)) {
    fcmItFs.copyFileSync(fcmProdSwSrc, fcmProdSwDest);
    config.files = config.files || [];
    config.files.push(
      {
        pattern: fcmProdSwDest.replace(/\\/g, "/"),
        included: false,
        served: true,
        watched: false,
      },
      {
        pattern: fcmSwStubDest.replace(/\\/g, "/"),
        included: false,
        served: true,
        watched: false,
      }
    );
    config.proxies = Object.assign({}, config.proxies, {
      "/service-worker.js": "/absolute" + fcmProdSwDest.replace(/\\/g, "/"),
      "/interfold-sw.js": "/absolute" + fcmSwStubDest.replace(/\\/g, "/"),
    });
    config.customHeaders = (config.customHeaders || []).concat([
      { match: "service-worker\\.js$", name: "Service-Worker-Allowed", value: "/" },
      { match: "service-worker\\.js$", name: "Cache-Control", value: "no-cache" },
      { match: "runtime-config\\.js$", name: "Cache-Control", value: "no-cache" },
    ]);
  }

  function fcmItWriteRuntimeConfig(callback) {
    if (!interfoldFcmBackendUrl) {
      callback(new Error("no backend URL for runtime-config.js"));
      return;
    }
    var url = interfoldFcmBackendUrl.replace(/\/$/, "") + "/api/settings/firebase-config?platform=web";
    fcmItHttp
      .get(url, function (res) {
        var body = "";
        res.on("data", function (c) { body += c; });
        res.on("end", function () {
          try {
            var parsed = JSON.parse(body);
            var data = parsed.data || parsed;
            var firebase = {
              apiKey: data.api_key || data.apiKey,
              authDomain: data.auth_domain || data.authDomain,
              projectId: data.project_id || data.projectId,
              storageBucket: data.storage_bucket || data.storageBucket || "",
              messagingSenderId: data.messaging_sender_id || data.messagingSenderId,
              appId: data.app_id || data.appId,
              vapidKey: data.vapid_key || data.vapidKey,
            };
            var js =
              "window.__INTERFOLD_RUNTIME_CONFIG__ = " +
              JSON.stringify({ firebase: firebase }) +
              ";\n" +
              'globalThis.__INTERFOLD_DEFAULT_API_ENDPOINT__ = "";\n';
            callback(null, js);
          } catch (e) {
            callback(e);
          }
        });
      })
      .on("error", callback);
  }

  function InterfoldFcmItMiddleware() {
    return function (req, res, next) {
      var parsedUrl = String(req.url || "");
      var pathOnly = parsedUrl.split("?")[0];
      if (pathOnly === "/__interfold_grant_notifications") {
        var origin = "";
        var q = parsedUrl.indexOf("?");
        if (q >= 0) {
          parsedUrl
            .slice(q + 1)
            .split("&")
            .forEach(function (part) {
              var eq = part.indexOf("=");
              var key = eq >= 0 ? decodeURIComponent(part.slice(0, eq)) : part;
              if (key === "origin") {
                origin = eq >= 0 ? decodeURIComponent(part.slice(eq + 1)) : "";
              }
            });
        }
        fcmItCdpGrant(origin)
          .then(function (summary) {
            res.statusCode = 200;
            res.setHeader("Content-Type", "application/json");
            res.end(JSON.stringify(summary || { granted: true }));
          })
          .catch(function (err) {
            console.warn("[interfold-fcm] CDP grant failed:", err && err.message ? err.message : err);
            res.statusCode = 500;
            res.setHeader("Content-Type", "text/plain");
            res.end(String(err && err.message ? err.message : err));
          });
        return;
      }
      if (pathOnly === "/runtime-config.js") {
        fcmItWriteRuntimeConfig(function (err, js) {
          if (err) {
            res.statusCode = 500;
            res.setHeader("Content-Type", "text/plain");
            res.end(String(err && err.message ? err.message : err));
            return;
          }
          res.statusCode = 200;
          res.setHeader("Content-Type", "application/javascript");
          res.end(js);
        });
        return;
      }
      next();
    };
  }
  InterfoldFcmItMiddleware.$inject = [];
  config.plugins = config.plugins || [];
  config.plugins.push({
    "middleware:interfold-fcm-it": ["factory", InterfoldFcmItMiddleware],
  });
  config.middleware = (config.middleware || []).concat("interfold-fcm-it");

  var fcmGrantHook = fcmItPath.join(__dirname, "fcm-grant-hook.js");
  fcmItFs.writeFileSync(
    fcmGrantHook,
    [
      "(function () {",
      "  var origin = location.origin;",
      "  fetch('/__interfold_grant_notifications?origin=' + encodeURIComponent(origin))",
      "    .then(function (res) {",
      "      return res.text().then(function (body) {",
      "        globalThis.__INTERFOLD_FCM_GRANT__ = { ok: res.ok, status: res.status, body: body, origin: origin };",
      "        if (!res.ok) console.warn('[interfold-fcm] CDP grant failed: ' + body);",
      "      });",
      "    })",
      "    .catch(function (err) {",
      "      globalThis.__INTERFOLD_FCM_GRANT__ = { ok: false, error: String(err), origin: origin };",
      "      console.warn('[interfold-fcm] CDP grant error: ' + err);",
      "    });",
      "})();",
      "",
    ].join("\n")
  );
  config.files = config.files || [];
  config.files.unshift({
    pattern: fcmGrantHook.replace(/\\/g, "/"),
    included: true,
    served: true,
    watched: false,
  });
}
