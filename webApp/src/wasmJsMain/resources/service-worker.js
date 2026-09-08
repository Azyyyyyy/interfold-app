// APP_VERSION is stamped at build time by :webApp:generateServiceWorkerPrecache
// from the central app version (see root build.gradle.kts). Any change to the
// stamped value forces the activate handler at the bottom of this file to
// delete the previous cache and repopulate it.
const APP_VERSION = '__APP_VERSION__';
const CACHE_NAME = `interfold-app-cache-${APP_VERSION}`;
const PRECACHE_URLS = [];
//^^ This is populated at build time with the actual list of files to precache;
// based on the output of the bundler. See build.gradle.kts for details.

// -----------------------------------------------------------------------------
// Firebase Cloud Messaging integration
// -----------------------------------------------------------------------------
// The Firebase JS SDK compat build is imported at SW top level so that
// firebase.messaging().onBackgroundMessage(...) can register its own `push`
// listener. Config is fetched from the API server (see FirebaseConfigProvider on
// the Kotlin side) and mirrored into a dedicated Cache Storage entry so a cold
// SW wake-up after a browser restart can re-init from disk before the network
// is even needed.

const FIREBASE_CONFIG_CACHE = 'interfold-firebase-config-v1';
const FIREBASE_CONFIG_URL = '/api/settings/firebase-config?platform=web';

try {
  importScripts(
    'https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js',
    'https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js'
  );
} catch (e) {
  console.warn('[SW] Failed to importScripts for Firebase:', e);
}

function normaliseFirebaseConfig(raw) {
  // The API envelope wraps the payload in { data: {...}, error: null }.
  const src = raw && raw.data;
  if (!src) return null;
  return {
    apiKey: src.api_key,
    authDomain: src.auth_domain,
    projectId: src.project_id,
    storageBucket: src.storage_bucket,
    messagingSenderId: src.messaging_sender_id,
    appId: src.app_id
    // vapidKey is used only in the main-thread getToken call, not here.
  };
}

async function readCachedFirebaseConfig() {
  try {
    const cache = await caches.open(FIREBASE_CONFIG_CACHE);
    const cached = await cache.match(FIREBASE_CONFIG_URL);
    if (!cached) return null;
    return normaliseFirebaseConfig(await cached.json());
  } catch (e) {
    console.warn('[SW] readCachedFirebaseConfig failed:', e);
    return null;
  }
}

async function fetchAndCacheFirebaseConfig() {
  try {
    const resp = await fetch(FIREBASE_CONFIG_URL, { credentials: 'omit' });
    if (!resp || resp.status !== 200) return null;
    const cache = await caches.open(FIREBASE_CONFIG_CACHE);
    await cache.put(FIREBASE_CONFIG_URL, resp.clone());
    return normaliseFirebaseConfig(await resp.json());
  } catch (e) {
    console.warn('[SW] fetchAndCacheFirebaseConfig failed:', e);
    return null;
  }
}

let firebaseInitPromise = null;

function ensureFirebaseInitialized() {
  if (firebaseInitPromise) return firebaseInitPromise;
  firebaseInitPromise = (async () => {
    if (typeof firebase === 'undefined' || !firebase.messaging) {
      console.warn('[SW] Firebase SDK not available (importScripts failed)');
      return false;
    }
    let config = await readCachedFirebaseConfig();
    if (!config) {
      config = await fetchAndCacheFirebaseConfig();
    }
    if (!config || !config.apiKey || !config.projectId) {
      console.warn('[SW] No usable Firebase config; skipping SW-side FCM init');
      return false;
    }
    if (!firebase.apps.length) {
      firebase.initializeApp(config);
    }
    const messaging = firebase.messaging();
    messaging.onBackgroundMessage((payload) => {
      try {
        const notif = (payload && payload.notification) || {};
        const title = notif.title || 'Interfold';
        const options = {
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
  })().catch((e) => {
    console.warn('[SW] Firebase init failed:', e);
    firebaseInitPromise = null;
    return false;
  });
  return firebaseInitPromise;
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const deepLink = event.notification.data && event.notification.data.deep_link;
  const target = deepLink || '/';
  event.waitUntil((async () => {
    const clientList = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of clientList) {
      if (client.url === target || client.url.endsWith(target)) {
        return client.focus();
      }
    }
    return self.clients.openWindow(target);
  })());
});

// -----------------------------------------------------------------------------
// Offline caching lifecycle
// -----------------------------------------------------------------------------

self.addEventListener('install', (event) => {
  console.log('[SW] Install - precaching + Firebase init');
  // Pre-cache the static assets we know should exist. Be tolerant of
  // individual fetch failures so install doesn't fail if some build
  // artifact is missing during development.
  event.waitUntil(
    Promise.all([
      caches.open(CACHE_NAME).then((cache) =>
        Promise.all(PRECACHE_URLS.map((url) =>
          fetch(url).then((resp) => {
            if (resp && resp.status === 200) {
              return cache.put(url, resp.clone());
            }
            return undefined;
          }).catch(() => undefined)
        ))
      ),
      // Kick off Firebase init but never let it break install. Failures here
      // just mean no background pushes; foreground push and offline caching
      // still work.
      ensureFirebaseInitialized().catch(() => false)
    ]).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  console.log('[SW] Activate - clearing old caches + ensuring Firebase');
  event.waitUntil(
    Promise.all([
      caches.keys().then((keys) => Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME && key !== FIREBASE_CONFIG_CACHE) return caches.delete(key);
          return null;
        })
      )),
      ensureFirebaseInitialized().catch(() => false)
    ]).then(() => self.clients.claim())
  );
});

function fetchAndCache(request) {
  return caches.open(CACHE_NAME).then((cache) =>
    fetch(request).then((response) => {
      if (response && response.status === 200) {
        cache.put(request, response.clone());
      }
      return response;
    }).catch(() =>
      caches.match(request).then((cached) => cached || new Response('', { status: 503, statusText: 'Service Unavailable' }))
    )
  );
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);

  // Only handle same-origin requests
  if (url.origin !== self.location.origin) return;

  // API requests - network-first, fallback to cache
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      fetch(req).then((resp) => resp).catch(() => caches.match(req))
    );
    return;
  }

  // Navigation requests - network-first so we get updated app shell
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req).then((networkResp) => {
        return caches.open(CACHE_NAME).then((cache) => {
          try {
            cache.put('/index.html', networkResp.clone());
          } catch (e) {
            // ignore cache put failures
          }
          return networkResp;
        });
      }).catch(() => caches.match('/index.html'))
    );
    return;
  }

  // Assets (JS, WASM, CSS, images) - stale-while-revalidate: serve cache immediately, update in background
  const isAsset = req.destination === 'script' ||
                  req.destination === 'style' ||
                  req.destination === 'image' ||
                  req.url.endsWith('.wasm') ||
                  req.url.endsWith('.js') ||
                  req.url.endsWith('.css') ||
                  req.url.includes('/lib/');

  if (isAsset) {
    event.respondWith(
      caches.match(req).then((cachedResp) => {
        const networkFetch = fetch(req).then((networkResp) => {
          if (networkResp && networkResp.status === 200) {
            return caches.open(CACHE_NAME).then((cache) => {
              cache.put(req, networkResp.clone());
              return networkResp;
            });
          }
          return networkResp;
        }).catch(() => caches.match(req)).then((resp) => resp || new Response('', { status: 503, statusText: 'Service Unavailable' }));
        event.waitUntil(networkFetch);
        return cachedResp || networkFetch;
      })
    );
    return;
  }

  // Default fallback: try cache, then network
  event.respondWith(caches.match(req).then((resp) => resp || fetchAndCache(req)));
});
