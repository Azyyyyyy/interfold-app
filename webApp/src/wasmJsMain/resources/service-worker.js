// APP_VERSION and PRECACHE_URLS are stamped at build time by
// :webApp:generateServiceWorkerPrecache. Cache / pin-vs-apply / fetch
// handlers live in the Kotlin/JS bundle imported below
// (`:pwa-service-worker-js`). Firebase compat + FCM stay here because
// they are already classic JS (importScripts + firebase-app-compat).
const APP_VERSION = '__APP_VERSION__';
const PRECACHE_URLS = [];
//^^ This is populated at build time with the actual list of files to precache;
// based on the output of the bundler. See build.gradle.kts for details.

try {
  importScripts(
    'https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js',
    'https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js'
  );
} catch (e) {
  console.warn('[SW] Failed to importScripts for Firebase:', e);
}

let firebaseInitPromise = null;

function readRuntimeConfig() {
  return fetch('/runtime-config.js', { cache: 'no-store' })
    .then((res) => (res.ok ? res.text() : Promise.reject(new Error('runtime-config.js ' + res.status))))
    .then((text) => {
      const match = text.match(/window\.__INTERFOLD_RUNTIME_CONFIG__\s*=\s*(\{[\s\S]*?\});/);
      if (!match) throw new Error('runtime-config.js missing window.__INTERFOLD_RUNTIME_CONFIG__');
      return JSON.parse(match[1]);
    });
}

function getFirebaseConfigFromRuntime(config) {
  const firebaseConfig = config && config.firebase;
  if (!firebaseConfig || !firebaseConfig.apiKey || !firebaseConfig.projectId || !firebaseConfig.appId || !firebaseConfig.messagingSenderId) {
    return null;
  }
  return {
    apiKey: firebaseConfig.apiKey,
    authDomain: firebaseConfig.authDomain,
    projectId: firebaseConfig.projectId,
    storageBucket: firebaseConfig.storageBucket,
    messagingSenderId: firebaseConfig.messagingSenderId,
    appId: firebaseConfig.appId,
    measurementId: firebaseConfig.measurementId,
  };
}

function ensureFirebaseInitialized() {
  if (firebaseInitPromise) return firebaseInitPromise;
  firebaseInitPromise = readRuntimeConfig()
    .then((runtimeConfig) => {
      const firebaseConfig = getFirebaseConfigFromRuntime(runtimeConfig);
      if (!firebaseConfig) {
        console.log('[SW] Firebase not configured; skipping FCM init');
        return false;
      }
      if (typeof firebase === 'undefined') {
        console.warn('[SW] Firebase compat SDK not loaded');
        return false;
      }
      if (!firebase.apps.length) {
        firebase.initializeApp(firebaseConfig);
      }
      const messaging = firebase.messaging();
      messaging.onBackgroundMessage((payload) => {
        const notification = (payload && payload.notification) || {};
        const data = (payload && payload.data) || {};
        const title = notification.title || data.title || 'Interfold';
        const body = notification.body || data.body || '';
        return self.registration.showNotification(title, {
          body: body,
          icon: '/icons/icon-192.png',
          badge: '/icons/icon-192.png',
          data: data,
        });
      });
      return true;
    })
    .catch((error) => {
      console.warn('[SW] Firebase init failed:', error);
      firebaseInitPromise = null;
      return false;
    });
  return firebaseInitPromise;
}

self.addEventListener('install', (event) => {
  event.waitUntil(ensureFirebaseInitialized().catch(() => false));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(ensureFirebaseInitialized().catch(() => false));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const deepLink = event.notification.data && event.notification.data.deep_link;
  const target = deepLink || '/';
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];
        if (client.url === target || client.url.endsWith(target)) {
          return client.focus();
        }
      }
      return self.clients.openWindow(target);
    }),
  );
});

importScripts('/interfold-sw.js');
InterfoldServiceWorker.start(APP_VERSION, PRECACHE_URLS);
