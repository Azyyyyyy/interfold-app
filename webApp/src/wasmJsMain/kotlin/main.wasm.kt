import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import app.interfold.PlatformEventBus
import app.interfold.app.Settings
import app.interfold.app.ui.compose.screens.RootScreen
import app.interfold.app.ui.model.RootComponentImpl
import app.interfold.app.utils.FirebaseConfig
import app.interfold.app.utils.FirebaseConfigProvider
import app.interfold.app.utils.FirebasePlatform
import app.interfold.app.utils.PlatformEvent
import app.interfold.app.utils.SETTINGS_LOCALSTORAGE_KEY
import app.interfold.app.utils.globalSerializer
import app.interfold.app.utils.migrateLegacyWasmSettings
import app.interfold.app.utils.platformUtilities
import app.interfold.app.utils.tryRefreshWebFCMToken
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.stop
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import app.interfold.app.utils.PublicKeyProvider
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.Document
import org.w3c.dom.url.URLSearchParams
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
fun main() {
  migrateLegacyWasmSettings()
  val lifecycle = LifecycleRegistry()
  // Shared with `platform.wasm.kt` so the reinit path fired from
  // `SettingsInterfaceImpl.setToken` on login completion can emit into the same
  // flow this component's collector consumes.
  val platformEventFlow = PlatformEventBus.flow

  val token = tryGetToken()

  consoleLog("Got token: $token")

  var initialSettings = localStorage.getItem(SETTINGS_LOCALSTORAGE_KEY)?.let {
    Settings.deserialize(it)
  } ?: Settings()

  // Only prefetch server-hosted config when the user is logged in
  val isLoggedIn = initialSettings.token != null

  // Restore persisted public key from localStorage if within TTL and asynchronously refresh
  try {
    val initialStoredPem = localStorage.getItem("PUBLIC_KEY_PEM")
    val initialStoredAt = localStorage.getItem("PUBLIC_KEY_AT")?.toLongOrNull() ?: 0L
    val now = Clock.System.now().toEpochMilliseconds()

    GlobalScope.launch {
      try {
        var pemToCache = initialStoredPem
        var atToCache = initialStoredAt

        if (isLoggedIn && (pemToCache == null || (now - atToCache) >= PublicKeyProvider.TTL_MS)) {
          try {
            val freshKey = PublicKeyProvider.getPublicKey("${initialSettings.apiEndpoint}/api")
            pemToCache = freshKey
            atToCache = now
            localStorage.setItem("PUBLIC_KEY_PEM", freshKey)
            localStorage.setItem("PUBLIC_KEY_AT", now.toString())
          } catch (e: Exception) {
            consoleLog("Failed to refresh public key: ${e.message ?: e.toString()}")
          }
        }

        pemToCache?.let {
          PublicKeyProvider.setCachedKey(it, atToCache)
        }
      } catch (e: Exception) {
        consoleLog("Error in startup public key coroutine: ${e.message ?: e.toString()}")
      }
    }
  } catch (e: Exception) {
    consoleLog("Failed to initiate public key restoration: $e")
  }

  // Restore persisted Firebase web config from localStorage if within TTL and asynchronously refresh
  try {
    val initialStoredConfig = localStorage.getItem("FIREBASE_CONFIG_JSON")
    val initialStoredAt = localStorage.getItem("FIREBASE_CONFIG_AT")?.toLongOrNull() ?: 0L
    val now = Clock.System.now().toEpochMilliseconds()

    GlobalScope.launch {
      try {
        var configJsonToCache = initialStoredConfig
        var atToCache = initialStoredAt

        if (isLoggedIn && (configJsonToCache == null || (now - atToCache) >= FirebaseConfigProvider.TTL_MS)) {
          try {
            val fresh = FirebaseConfigProvider.getConfig(
              FirebasePlatform.WEB,
              "${initialSettings.apiEndpoint}/api"
            )
            if (fresh is FirebaseConfig.Web) {
              val json = globalSerializer.encodeToString(fresh)
              configJsonToCache = json
              atToCache = now
              localStorage.setItem("FIREBASE_CONFIG_JSON", json)
              localStorage.setItem("FIREBASE_CONFIG_AT", now.toString())
            }
          } catch (e: Exception) {
            consoleLog("Failed to refresh Firebase config: ${e.message ?: e.toString()}")
          }
        }

        configJsonToCache?.let { json ->
          try {
            val restored: FirebaseConfig.Web = globalSerializer.decodeFromString(json)
            FirebaseConfigProvider.setCachedConfig(restored, FirebasePlatform.WEB, atToCache)
          } catch (e: Exception) {
            consoleLog("Failed to decode cached Firebase config: ${e.message ?: e.toString()}")
          }
        }
      } catch (e: Exception) {
        consoleLog("Error in startup Firebase config coroutine: ${e.message ?: e.toString()}")
      }
    }
  } catch (e: Exception) {
    consoleLog("Failed to initiate Firebase config restoration: $e")
  }

  if(token != null) {
    initialSettings = initialSettings.copy(token = token)
  }

  platformUtilities.initialize(initialSettings)

  // Silent cold-start refresh: if push is already opted-in and we have a session,
  // acquire an FCM token without prompting for permission (browser has already granted
  // it on a previous session) and route it through the same platformEventFlow the
  // mobile platforms use. Skips cleanly if permission was revoked, config is missing,
  // etc. — RootScreen's collector picks it up if it fires.
  if (initialSettings.showPushNotifications
    && initialSettings.token != null
    && !initialSettings.tokenIsProtected
  ) {
    GlobalScope.launch {
      val fcmToken = tryRefreshWebFCMToken("${initialSettings.apiEndpoint}/api")
      if (fcmToken != null) {
        platformEventFlow.emit(PlatformEvent.PushNotificationTokenReceived(fcmToken))
      }
    }
  }

  val rootComponent = RootComponentImpl(
    componentContext = DefaultComponentContext(lifecycle = lifecycle),
    initialSettings = initialSettings,
    coroutineContext = Dispatchers.Main,
    platformUtilities = platformUtilities,
    platformEventFlow = platformEventFlow,
    deepLinkURL = null
  )

  lifecycle.attachToDocument()

  ComposeViewport("composeApp") {
    RootScreen(rootComponent)
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun consoleLog(text: String): Unit = js("console.log(text)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun tryGetToken(): String? {
  consoleLog("Trying to get token")
  val params = URLSearchParams(window.location.search.toJsString())

  return params.get("token").also {
    consoleLog("Token is: $it")
    if(it != null) {
      window.history.replaceState(null, document.title, "/")
      consoleLog("Token is not null; nuking history URL")
    }
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
@Suppress("unused")
@JsFun("(document) => document.visibilityState")
private external fun visibilityState(document: Document): String

private fun LifecycleRegistry.attachToDocument() {
  fun onVisibilityChanged() {
    if (visibilityState(document) == "visible") {
      resume()
    } else {
      stop()
    }
  }

  onVisibilityChanged()
  document.addEventListener(type = "visibilitychange", callback = { onVisibilityChanged() })
}
