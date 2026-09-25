package app.interfold.sw

import app.interfold.app.utils.AppUpdatePolicy
import kotlin.js.Promise

internal class ServiceWorkerRuntime(
  private val appVersion: String,
  private val precacheUrls: List<String>,
) {
  private val cacheName: String = AppUpdatePolicy.appCacheName(appVersion)
  private var servingCacheName: String = cacheName
  private var attached: Boolean = false

  fun attach() {
    if (attached) return
    attached = true
    addSelfEventListener("install") { event -> onInstall(event) }
    addSelfEventListener("activate") { event -> onActivate(event) }
    addSelfEventListener("message") { event -> onMessage(event) }
    addSelfEventListener("fetch") { event -> onFetch(event) }
  }

  private fun onInstall(event: dynamic) {
    consoleLog("[SW] Install - precaching")
    waitUntil(event, precacheAssets())
  }

  private fun precacheAssets(): Promise<dynamic> {
    val name = cacheName
    val urls = listToJsArray(precacheUrls)
    return asPromise(js(
      """
      caches.open(name).then(function(cache) {
        return Promise.all(urls.map(function(url) {
          return fetch(url).then(function(resp) {
            if (resp && resp.status === 200) {
              return cache.put(url, resp.clone());
            }
            return undefined;
          }).catch(function() { return undefined; });
        }));
      })
      """
    ))
  }

  private fun onActivate(event: dynamic) {
    consoleLog("[SW] Activate - decide serving cache")
    val work = thenAny(
      thenAny(decideServingCache()) { clientsClaim() },
    ) { broadcastAppVersion(appVersion) }
    waitUntil(event, work)
  }

  private fun decideServingCache(): Promise<dynamic> {
    val currentCacheName = cacheName
    val currentVersion = appVersion
    val prefix = AppUpdatePolicy.APP_CACHE_PREFIX
    val applyKey = APPLY_KEY
    val servingKey = SERVING_CACHE_KEY
    val meta = AppUpdatePolicy.SW_META_CACHE
    return thenAny(
      asPromise(
        js(
          """
      Promise.all([
        caches.open(meta).then(function(c) { return c.match(applyKey).then(function(r) { return r ? r.text() : ''; }); }),
        caches.open(meta).then(function(c) { return c.match(servingKey).then(function(r) { return r ? r.text() : ''; }); }),
        caches.keys()
      ])
      """
        ),
      ),
    ) { triple ->
      val applyVersion = triple[0]?.toString().orEmpty()
      val pinned = triple[1]?.toString().orEmpty()
      val keys = jsArrayToList(triple[2])
      val previous = keys.filter { it.startsWith(prefix) && it != currentCacheName }
      thenAny(collectShellCacheNames(previous)) { shells ->
        val shellNames = jsArrayToList(shells)
        val decision = AppUpdatePolicy.servingCacheAfterActivate(
          currentCacheName = currentCacheName,
          currentVersion = currentVersion,
          applyRequestedVersion = applyVersion.ifEmpty { null },
          previousCacheNames = previous,
          pinnedCacheName = pinned.ifEmpty { null },
          hasAppShell = { it in shellNames },
        )
        if (decision.applied) {
          applyUpdateNow()
        } else {
          servingCacheName = decision.servingCacheName
          writeMeta(SERVING_CACHE_KEY, servingCacheName)
        }
      }
    }
  }

  private fun applyUpdateNow(): Promise<dynamic> {
    servingCacheName = cacheName
    val current = cacheName
    return thenAny(cachesKeys()) { keysDyn ->
      val toDelete = AppUpdatePolicy.cachesToDeleteOnApply(jsArrayToList(keysDyn), current)
      val deletes = toDelete.map { cachesDelete(it) }.toTypedArray()
      thenAny(promiseAll(deletes)) {
        thenAny(writeMeta(SERVING_CACHE_KEY, current)) {
          deleteMeta(APPLY_KEY)
        }
      }
    }
  }

  private fun writeMeta(key: String, value: String): Promise<dynamic> {
    val meta = AppUpdatePolicy.SW_META_CACHE
    return asPromise(js(
      """
      caches.open(meta).then(function(cache) {
        return cache.put(key, new Response(value, { headers: { 'content-type': 'text/plain' } }));
      })
      """
    ))
  }

  private fun deleteMeta(key: String): Promise<dynamic> {
    val meta = AppUpdatePolicy.SW_META_CACHE
    return asPromise(js(
      """
      caches.open(meta).then(function(cache) { return cache.delete(key); })
      """
    ))
  }

  private fun onMessage(event: dynamic) {
    val data = event.data ?: return
    val type = data["type"]?.toString() ?: return
    if (type == "SKIP_WAITING") {
      waitUntil(
        event,
        thenAny(thenAny(writeMeta(APPLY_KEY, appVersion)) { applyUpdateNow() }) { skipWaiting() },
      )
      return
    }
    if (type == "interfold-app-version-request") {
      val source = event.source
      if (source != null) {
        postVersionToSource(source, appVersion)
      }
    }
  }

  private fun onFetch(event: dynamic) {
    val req = event.request
    val url = js("new URL(req.url)")
    if (url.origin != selfOrigin()) return

    val pathname = url.pathname as String
    if (pathname == "/runtime-config.js") {
      respondWith(event, fetchNoStore(req))
      return
    }
    if (pathname.startsWith("/api/")) {
      respondWith(
        event,
        catchAny(fetchRaw(req)) { cachesMatch(req) },
      )
      return
    }

    val mode = req.mode?.toString()
    if (mode == "navigate") {
      respondWith(event, fetchCacheFirst(req, "/index.html"))
      return
    }

    val destination = req.destination?.toString().orEmpty()
    val isAppBinary = destination == "script" ||
      pathname.endsWith(".wasm") ||
      pathname.endsWith(".js") ||
      pathname.endsWith(".mjs")
    if (isAppBinary) {
      respondWith(event, fetchCacheFirst(req, null))
      return
    }

    val isStaticAsset = destination == "style" ||
      destination == "image" ||
      destination == "font" ||
      pathname.endsWith(".css") ||
      pathname.contains("/lib/")
    if (isStaticAsset) {
      respondWith(event, fetchStatic(event, req))
      return
    }

    respondWith(
      event,
      thenAny(cachesMatch(req)) { cached -> cached ?: fetchAndCache(req) },
    )
  }

  private fun pinnedToPreviousBuild(): Boolean {
    return !AppUpdatePolicy.mayFetchAppBinaryFromNetwork(servingCacheName, cacheName)
  }

  private fun fetchCacheFirst(request: dynamic, cacheKey: String?): Promise<dynamic> {
    val serving = servingCacheName
    val key = cacheKey ?: request
    val allowNetwork = !pinnedToPreviousBuild()
    return asPromise(js(
      """
      caches.open(serving).then(function(cache) {
        return cache.match(key).then(function(cached) {
          if (cached) return cached;
          if (!allowNetwork) {
            return new Response('', { status: 503, statusText: 'Service Unavailable' });
          }
          return fetch(request).then(function(networkResp) {
            if (networkResp && networkResp.status === 200) {
              try { cache.put(key, networkResp.clone()); } catch (e) {}
            }
            return networkResp;
          }).catch(function() {
            return new Response('', { status: 503, statusText: 'Service Unavailable' });
          });
        });
      })
      """
    ))
  }

  private fun fetchAndCache(request: dynamic): Promise<dynamic> {
    val serving = servingCacheName
    val allowNetwork = !pinnedToPreviousBuild()
    return asPromise(js(
      """
      caches.open(serving).then(function(cache) {
        return cache.match(request).then(function(cached) {
          if (cached) return cached;
          if (!allowNetwork) {
            return new Response('', { status: 503, statusText: 'Service Unavailable' });
          }
          return fetch(request).then(function(response) {
            if (response && response.status === 200) {
              cache.put(request, response.clone());
            }
            return response;
          }).catch(function() {
            return new Response('', { status: 503, statusText: 'Service Unavailable' });
          });
        });
      })
      """
    ))
  }

  private fun fetchStatic(event: dynamic, request: dynamic): Promise<dynamic> {
    val serving = servingCacheName
    val pinned = pinnedToPreviousBuild()
    return asPromise(js(
      """
      caches.open(serving).then(function(cache) {
        return cache.match(request).then(function(cachedResp) {
          if (pinned) {
            return cachedResp || fetch(request).catch(function() {
              return new Response('', { status: 503, statusText: 'Service Unavailable' });
            });
          }
          var networkFetch = fetch(request).then(function(networkResp) {
            if (networkResp && networkResp.status === 200) {
              cache.put(request, networkResp.clone());
            }
            return networkResp;
          }).catch(function() { return cache.match(request); }).then(function(resp) {
            return resp || new Response('', { status: 503, statusText: 'Service Unavailable' });
          });
          event.waitUntil(networkFetch);
          return cachedResp || networkFetch;
        });
      })
      """
    ))
  }

}
