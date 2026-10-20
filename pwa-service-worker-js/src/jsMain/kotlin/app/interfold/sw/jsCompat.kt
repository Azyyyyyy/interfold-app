package app.interfold.sw

import app.interfold.app.utils.AppUpdatePolicy
import kotlin.js.Promise

internal const val SERVING_CACHE_KEY = "/__serving-cache-name"
internal const val APPLY_KEY = "/__apply-update-version"

internal fun jsArrayToList(value: dynamic): List<String> {
  val result = mutableListOf<String>()
  val length = value.length as Int
  for (i in 0 until length) {
    result.add(value[i].toString())
  }
  return result
}

internal fun listToJsArray(values: List<String>): Array<String> = values.toTypedArray()

@Suppress("UNUSED_PARAMETER")
internal fun cachesOpen(name: String): Promise<dynamic> = js("caches.open(name)")

internal fun cachesKeys(): Promise<dynamic> = js("caches.keys()")

@Suppress("UNUSED_PARAMETER")
internal fun cachesDelete(name: String): Promise<dynamic> = js("caches.delete(name)")

@Suppress("UNUSED_PARAMETER")
internal fun cachesMatch(request: dynamic): Promise<dynamic> = js("caches.match(request)")

@Suppress("UNUSED_PARAMETER")
internal fun fetchOmitCreds(url: String): Promise<dynamic> =
  js("fetch(url, { credentials: 'omit' })")

internal fun unavailableResponse(): dynamic =
  js("new Response('', { status: 503, statusText: 'Service Unavailable' })")

@Suppress("UNUSED_PARAMETER")
internal fun textResponse(value: String): dynamic =
  js("new Response(value, { headers: { 'content-type': 'text/plain' } })")

@Suppress("UNUSED_PARAMETER")
internal fun promiseAll(items: dynamic): Promise<dynamic> = js("Promise.all(items)")

internal fun promiseResolve(value: dynamic): Promise<dynamic> = Promise.resolve(value)

internal fun asPromise(value: dynamic): Promise<dynamic> = value.unsafeCast<Promise<dynamic>>()

@Suppress("UNUSED_PARAMETER")
internal fun thenAny(promise: dynamic, transform: (dynamic) -> dynamic): Promise<dynamic> {
  return js("promise.then(transform)")
}

@Suppress("UNUSED_PARAMETER")
internal fun catchAny(promise: dynamic, transform: (dynamic) -> dynamic): Promise<dynamic> {
  return js("promise.catch(transform)")
}

@Suppress("UNUSED_PARAMETER")
internal fun addSelfEventListener(type: String, handler: (dynamic) -> Unit) {
  js("self.addEventListener(type, handler)")
}

@Suppress("UNUSED_PARAMETER")
internal fun waitUntil(event: dynamic, promise: Promise<dynamic>) {
  js("event.waitUntil(promise)")
}

@Suppress("UNUSED_PARAMETER")
internal fun respondWith(event: dynamic, promise: Promise<dynamic>) {
  js("event.respondWith(promise)")
}

@Suppress("UNUSED_PARAMETER")
internal fun consoleLog(message: String) {
  js("console.log(message)")
}

@Suppress("UNUSED_PARAMETER")
internal fun consoleWarn(message: String, error: dynamic = null) {
  js("console.warn(message, error)")
}

@Suppress("UNUSED_PARAMETER")
internal fun cacheHasAppShell(name: String): Promise<Boolean> {
  return js(
    """
    caches.open(name).then(function(cache) {
      return Promise.all([cache.match('/index.html'), cache.match('/interfold-app.js')])
        .then(function(hits) { return !!(hits[0] || hits[1]); });
    })
    """
  )
}

internal fun collectShellCacheNames(previous: List<String>): Promise<dynamic> {
  if (previous.isEmpty()) return Promise.resolve(emptyArray<String>())
  val names = listToJsArray(previous)
  return asPromise(
    js(
      """
    Promise.all(names.map(function(name) {
      return caches.open(name).then(function(cache) {
        return Promise.all([cache.match('/index.html'), cache.match('/interfold-app.js')])
          .then(function(hits) { return (hits[0] || hits[1]) ? name : null; });
      });
    })).then(function(rows) { return rows.filter(Boolean); })
    """
    ),
  )
}

@Suppress("UNUSED_PARAMETER")
internal fun skipWaiting(): Promise<dynamic> = js("self.skipWaiting()")

internal fun clientsClaim(): Promise<dynamic> = js("self.clients.claim()")

@Suppress("UNUSED_PARAMETER")
internal fun broadcastAppVersion(version: String): Promise<dynamic> {
  return js(
    """
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clients) {
      clients.forEach(function(client) {
        client.postMessage({ type: 'interfold-app-version', version: version });
      });
    })
    """
  )
}

@Suppress("UNUSED_PARAMETER")
internal fun postVersionToSource(source: dynamic, version: String) {
  js("source.postMessage({ type: 'interfold-app-version', version: version })")
}

internal fun selfOrigin(): String = js("self.location.origin") as String

/**
 * Same rule as [app.interfold.app.utils.AppUpdatePolicy.replayAccessRedirect].
 * Installed once so every fetch handler shares it.
 */
internal fun installAccessAwareFetch() {
  val replayAccessRedirect: (Boolean, String?) -> Boolean = { isNavigation, responseType ->
    AppUpdatePolicy.replayAccessRedirect(isNavigation, responseType)
  }
  js(
    """
    self.__interfoldAccessFetch = function(request) {
      var isNavigation = !!(request && request.mode === 'navigate');
      // No init on navigations: an init dictionary drops mode "navigate",
      // and Access answers that token refresh with 502.
      var pending = isNavigation
        ? fetch(request)
        : fetch(request, { credentials: 'include', redirect: 'manual' });
      return pending.then(function(resp) {
        var type = resp ? resp.type : null;
        if (replayAccessRedirect(isNavigation, type)) return resp;
        if (type === 'opaqueredirect') return null;
        return resp;
      });
    };
    """
  )
}

@Suppress("UNUSED_PARAMETER")
internal fun accessAwareFetch(request: dynamic): Promise<dynamic> =
  asPromise(js("self.__interfoldAccessFetch(request)"))
