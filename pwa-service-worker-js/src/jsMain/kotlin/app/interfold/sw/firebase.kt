package app.interfold.sw

import kotlin.js.Promise

private var firebaseInitPromise: Promise<dynamic>? = null

internal fun ensureFirebaseInitialized(): Promise<dynamic> {
  firebaseInitPromise?.let { return it }
  val started = catchAny(startFirebaseInit()) { error ->
    consoleWarn("[SW] Firebase init failed:", error)
    firebaseInitPromise = null
    false
  }
  firebaseInitPromise = started
  return started
}

private fun startFirebaseInit(): Promise<dynamic> {
  return asPromise(
    js(
      """
    (function() {
      if (typeof firebase === 'undefined' || !firebase.messaging) {
        console.warn('[SW] Firebase SDK not available (importScripts failed)');
        return Promise.resolve(false);
      }
      function normalise(raw) {
        var src = raw && raw.data;
        if (!src) return null;
        return {
          apiKey: src.api_key,
          authDomain: src.auth_domain,
          projectId: src.project_id,
          storageBucket: src.storage_bucket,
          messagingSenderId: src.messaging_sender_id,
          appId: src.app_id
        };
      }
      var cacheName = 'interfold-firebase-config-v1';
      var url = '/api/settings/firebase-config?platform=web';
      return caches.open(cacheName).then(function(cache) {
        return cache.match(url);
      }).then(function(cached) {
        if (!cached) return null;
        return cached.json().then(normalise);
      }).catch(function(e) {
        console.warn('[SW] readCachedFirebaseConfig failed:', e);
        return null;
      }).then(function(config) {
        if (config) return config;
        return fetch(url, { credentials: 'omit' }).then(function(resp) {
          if (!resp || resp.status !== 200) return null;
          return caches.open(cacheName).then(function(writeCache) {
            return writeCache.put(url, resp.clone()).then(function() {
              return resp.json().then(normalise);
            });
          });
        }).catch(function(e) {
          console.warn('[SW] fetchAndCacheFirebaseConfig failed:', e);
          return null;
        });
      }).then(function(config) {
        if (!config || !config.apiKey || !config.projectId) {
          console.warn('[SW] No usable Firebase config; skipping SW-side FCM init');
          return false;
        }
        if (!firebase.apps.length) {
          firebase.initializeApp(config);
        }
        var messaging = firebase.messaging();
        messaging.onBackgroundMessage(function(payload) {
          try {
            var notif = (payload && payload.notification) || {};
            var title = notif.title || 'Interfold';
            var options = {
              body: notif.body || '',
              icon: '/icons/icon-192.png',
              badge: '/icons/icon-72.png',
              data: (payload && payload.data) || {}
            };
            return self.registration.showNotification(title, options);
          } catch (e) {
            console.warn('[SW] onBackgroundMessage failed:', e);
          }
        });
        return true;
      });
    })()
    """
    ),
  )
}
