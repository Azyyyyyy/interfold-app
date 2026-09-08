@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import androidx.compose.runtime.Composable
import app.interfold.app.ui.model.interfaces.ApiInterface
import app.interfold.app.ui.model.interfaces.SettingsInterface
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

/**
 * Web/wasm actual for [InitPushNotifications]. Unlike mobile, we do not receive tokens
 * from an OS callback — token acquisition happens inline as part of enabling push, or
 * as a cold-start refresh triggered from `main.wasm.kt` via [tryRefreshWebFCMToken].
 *
 * Flow when enabling from Settings:
 * 1. Request browser notification permission (or short-circuit if already granted/denied).
 * 2. Register `/service-worker.js` and wait for it to become active. Same worker as the
 *    offline cache; it also holds `firebase-messaging-compat` in the background scope.
 * 3. Lazy-load Firebase JS SDK compat bundles via injected `<script>` tags.
 * 4. Fetch the web Firebase config from the server (with cache + TTL).
 * 5. Initialise Firebase in the main thread and bind a foreground `onMessage` handler
 *    that surfaces messages as a `Notification`.
 * 6. Call `firebase.messaging().getToken({ vapidKey, serviceWorkerRegistration })`.
 * 7. Stash the token via `api.provideFirebaseMessagingToken(...)` and let the common
 *    settings plumbing POST it to the server.
 */
@Composable
internal actual fun InitPushNotifications(): InitPushNotificationsCallbacks {
  return { token: String, api: ApiInterface, settings: SettingsInterface, _: PlatformUtilities ->
    // Token-received: mirrors the mobile actual. On wasm this only fires when
    // main.wasm.kt's cold-start refresh acquires a token silently, or when the enable
    // path emits one after user opt-in.
    api.provideFirebaseMessagingToken(token)
    if (settings.data.value.showPushNotifications) {
      api.updatePushNotificationToken()
    }
  } to { show: Boolean, api: ApiInterface, settings: SettingsInterface, utils: PlatformUtilities ->
    setShowPushNotifications(show, api, settings, utils)
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun setShowPushNotifications(
  show: Boolean,
  api: ApiInterface,
  settings: SettingsInterface,
  utils: PlatformUtilities
) {
  settings.setShowPushNotifications(
    showPushNotifications = show,
    showAlert = utils::showAlert,
    sendToken = api::updatePushNotificationToken,
    invalidateToken = api::invalidatePushNotificationToken,
    tryInit = { commit ->
      GlobalScope.launch {
        val token = try {
          acquireWebFCMToken(
            endpoint = "${settings.data.value.apiEndpoint}/api",
            requestPermissionIfNeeded = true
          )
        } catch (e: Throwable) {
          platformLog("PUSH", "Failed to acquire web FCM token: $e")
          null
        }

        if (token == null) {
          withContext(Dispatchers.Main) { commit(false) }
        } else {
          api.provideFirebaseMessagingToken(token)
          withContext(Dispatchers.Main) { commit(true) }
        }
      }
    }
  )
}

/**
 * Cold-start refresh entry point. Called from `main.wasm.kt` on startup when the user
 * already has `showPushNotifications = true` and the browser has previously granted
 * permission. Returns null if push cannot be re-established (permission revoked, no
 * SW support, network failure, etc.) — the app carries on as normal.
 */
suspend fun tryRefreshWebFCMToken(endpoint: String): String? {
  return try {
    acquireWebFCMToken(endpoint, requestPermissionIfNeeded = false)
  } catch (e: Throwable) {
    platformLog("PUSH", "Cold-start FCM refresh failed: $e")
    null
  }
}

private suspend fun acquireWebFCMToken(
  endpoint: String,
  requestPermissionIfNeeded: Boolean
): String? {
  if (!webPushSupported()) {
    platformLog("PUSH", "Browser lacks service worker / Notification / PushManager support")
    return null
  }

  val permission = when (val current = currentNotificationPermission()) {
    "granted" -> "granted"
    "denied" -> {
      platformLog("PUSH", "Notification permission is denied")
      return null
    }
    else -> {
      if (!requestPermissionIfNeeded) {
        platformLog("PUSH", "Notification permission not yet granted (state=$current) and prompt suppressed")
        return null
      }
      requestNotificationPermission().await<JsString>().toString()
    }
  }
  if (permission != "granted") return null

  val swRegistration = try {
    registerAndAwaitServiceWorker("/service-worker.js").await<JsAny>()
  } catch (e: Throwable) {
    platformLog("PUSH", "Service worker registration failed: $e")
    return null
  }

  try {
    loadFirebaseCompatSDK().await<JsAny?>()
  } catch (e: Throwable) {
    platformLog("PUSH", "Failed to load Firebase JS SDK: $e")
    return null
  }

  val config = FirebaseConfigProvider.getConfig(FirebasePlatform.WEB, endpoint) as? FirebaseConfig.Web
  if (config == null) {
    platformLog("PUSH", "Expected Web Firebase config but got a different variant")
    return null
  }

  initializeFirebaseApp(
    apiKey = config.apiKey,
    authDomain = config.authDomain,
    projectId = config.projectId,
    storageBucket = config.storageBucket.orEmpty(),
    messagingSenderId = config.messagingSenderId,
    appId = config.appId
  )
  bindForegroundMessageHandler()

  return try {
    getFCMToken(config.vapidKey, swRegistration).await<JsString>().toString()
  } catch (e: Throwable) {
    platformLog("PUSH", "firebase.messaging().getToken failed: $e")
    null
  }
}

/* --------------- JS bindings --------------- */

@JsFun("() => 'serviceWorker' in navigator && 'Notification' in window && 'PushManager' in window")
private external fun webPushSupported(): Boolean

@JsFun("() => (typeof Notification === 'undefined') ? 'unsupported' : Notification.permission")
private external fun currentNotificationPermission(): String

@JsFun("() => Notification.requestPermission()")
private external fun requestNotificationPermission(): Promise<JsString>

@JsFun(
  "(url) => { " +
    "if (!('serviceWorker' in navigator)) return Promise.reject(new Error('No service worker support')); " +
    "return navigator.serviceWorker.register(url).then(() => navigator.serviceWorker.ready); " +
    "}"
)
private external fun registerAndAwaitServiceWorker(url: String): Promise<JsAny>

@JsFun(
  "() => new Promise((resolve, reject) => { " +
    "if (typeof firebase !== 'undefined' && firebase.messaging) { resolve(null); return; } " +
    "const load = (src) => new Promise((res, rej) => { " +
    "const s = document.createElement('script'); s.src = src; s.crossOrigin = 'anonymous'; " +
    "s.onload = () => res(null); s.onerror = (e) => rej(e); document.head.appendChild(s); " +
    "}); " +
    "load('https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js')" +
    ".then(() => load('https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js'))" +
    ".then(() => resolve(null))" +
    ".catch(reject); " +
    "})"
)
private external fun loadFirebaseCompatSDK(): Promise<JsAny?>

@JsFun(
  "(apiKey, authDomain, projectId, storageBucket, messagingSenderId, appId) => { " +
    "if (firebase.apps && firebase.apps.length === 0) { " +
    "const cfg = { apiKey, authDomain, projectId, messagingSenderId, appId }; " +
    "if (storageBucket) cfg.storageBucket = storageBucket; " +
    "firebase.initializeApp(cfg); " +
    "} " +
    "return true; " +
    "}"
)
private external fun initializeFirebaseApp(
  apiKey: String,
  authDomain: String,
  projectId: String,
  storageBucket: String,
  messagingSenderId: String,
  appId: String
): Boolean

@JsFun(
  "(vapidKey, swRegistration) => firebase.messaging().getToken({ vapidKey: vapidKey, serviceWorkerRegistration: swRegistration })"
)
private external fun getFCMToken(vapidKey: String, swRegistration: JsAny): Promise<JsString>

@JsFun(
  "() => { " +
    "const messaging = firebase.messaging(); " +
    "if (messaging.__interfoldOnMessageBound) return true; " +
    "messaging.__interfoldOnMessageBound = true; " +
    "messaging.onMessage((payload) => { " +
    "try { " +
    "const notif = payload && payload.notification ? payload.notification : {}; " +
    "const title = notif.title || 'Interfold'; " +
    "const body = notif.body || ''; " +
    "if ('Notification' in window && Notification.permission === 'granted') { " +
    "new Notification(title, { body: body, icon: '/icons/icon-192.png', tag: 'interfold-fg' }); " +
    "} " +
    "} catch (e) { console.warn('[Interfold Push] Foreground handler failed:', e); } " +
    "}); " +
    "return true; " +
    "}"
)
private external fun bindForegroundMessageHandler(): Boolean
