package app.interfold.app
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import app.interfold.app.ui.compose.screens.RootScreen
import app.interfold.app.ui.model.RootComponent
import app.interfold.app.utils.FirebaseConfig
import app.interfold.app.utils.FirebaseConfigProvider
import app.interfold.app.utils.FirebasePlatform
import app.interfold.app.utils.PlatformDelegate
import app.interfold.app.utils.PlatformEvent
import app.interfold.app.utils.globalSerializer
import app.interfold.app.utils.platformLog
import app.interfold.app.utils.platformUtilities
import app.interfold.app.utils.sfSafariViewController
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackGestureOverlay
import com.arkivanov.essenty.backhandler.BackDispatcher
import io.ktor.http.Url
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSLog
import platform.UIKit.UIViewController
import kotlin.time.Clock

val platformEventFlow = MutableSharedFlow<PlatformEvent>(replay = 3)

@Suppress("unused", "FunctionName")
@OptIn(
  DelicateCoroutinesApi::class,
  ExperimentalDecomposeApi::class
)
fun MainViewController(platformDelegate: PlatformDelegate, root: RootComponent, backDispatcher: BackDispatcher): UIViewController {
  GlobalScope.launch {
    iosDeepLinkFlow.collect { deepLink ->
      if(deepLink != null) {
        NSLog("Deep link received by MainViewController: $deepLink")
        handleDeepLink(deepLink)

        // Close SFSafariViewController if it's open
        sfSafariViewController?.let {
          MainScope().launch {
            it.dismissViewControllerAnimated(true) {
              sfSafariViewController = null
            }
          }
        }
      }
    }
  }

  val settings = getSettingsFromKeychain()

  // Only prefetch server-hosted config when the user is logged in
  val isLoggedIn = settings.token != null

  // Restore persisted public key (if within TTL) and asynchronously refresh
  try {
    getStoredPublicKey()?.let { (pem, at) ->
      val now = Clock.System.now().toEpochMilliseconds()
      if (now - at < app.interfold.app.utils.PublicKeyProvider.TTL_MS) {
        runBlocking { app.interfold.app.utils.PublicKeyProvider.setCachedKey(pem, at) }
      }
    }

    if (isLoggedIn) {
      GlobalScope.launch {
        try {
          val key = app.interfold.app.utils.PublicKeyProvider.getPublicKey("${settings.apiEndpoint}/api")
          saveStoredPublicKey(key, Clock.System.now().toEpochMilliseconds())
        } catch (e: Exception) {
          NSLog("Failed to preload public key: $e")
        }
      }
    }
  } catch (e: Exception) {
    NSLog("Public key persistence restore failed: $e")
  }

  // Restore persisted Firebase config (if within TTL) and asynchronously refresh
  try {
    getStoredFirebaseConfig()?.let { (json, at) ->
      val now = Clock.System.now().toEpochMilliseconds()
      if (now - at < FirebaseConfigProvider.TTL_MS) {
        try {
          val restored: FirebaseConfig.IOS = globalSerializer.decodeFromString(json)
          runBlocking {
            FirebaseConfigProvider.setCachedConfig(restored, FirebasePlatform.IOS, at)
          }
        } catch (e: Exception) {
          NSLog("Failed to decode stored Firebase config: $e")
        }
      }
    }

    if (isLoggedIn) {
      GlobalScope.launch {
        try {
          val config = FirebaseConfigProvider.getConfig(FirebasePlatform.IOS, "${settings.apiEndpoint}/api")
          if (config is FirebaseConfig.IOS) {
            val json = globalSerializer.encodeToString(config)
            saveStoredFirebaseConfig(json, Clock.System.now().toEpochMilliseconds())
          }
        } catch (e: Exception) {
          NSLog("Failed to preload Firebase config: $e")
        }
      }
    }
  } catch (e: Exception) {
    NSLog("Firebase config persistence restore failed: $e")
  }

  platformUtilities.injectPlatformDelegate(platformDelegate)

  if(settings.showPushNotifications) {
    platformUtilities.performAdditionalPushNotificationSetup()
  }

  return ComposeUIViewController(configure = {
    // this.parallelRendering = true
    this.onFocusBehavior = OnFocusBehavior.DoNothing
  }) {
    PredictiveBackGestureOverlay(
      backDispatcher = backDispatcher,
      backIcon = null,
      endEdgeEnabled = false,
      modifier = Modifier.fillMaxSize(),
    ) {
      RootScreen(component = root)
    }
  }
}


@OptIn(DelicateCoroutinesApi::class)
fun providePushNotificationToken(token: String?) {
  token?.let {
    GlobalScope.launch {
      withContext(Dispatchers.Main) {
        platformEventFlow.emit(PlatformEvent.PushNotificationTokenReceived(token))
      }
    }
  }
}

/**
 * Swift-callable bridge that materialises the server-hosted iOS Firebase configuration
 * (fetching from the API or falling back to the on-disk cache in the Keychain). Called
 * from `AppDelegate.application(_:didFinishLaunchingWithOptions:)` before
 * `FirebaseApp.configure(options:)`.
 *
 * Runs before `MainViewController` has done its own Keychain restore, so we seed the
 * in-memory cache from KVault here first. When the user is logged out we skip the
 * network fetch entirely — before login the endpoint may not be finalised, and fetching
 * from the default endpoint would cache config for the wrong server.
 *
 * Returns `null` if we have no cached config and can't (or won't) fetch — the Swift
 * side should then skip `FirebaseApp.configure` and log; push simply won't work until
 * the next launch after the user logs in.
 */
@Suppress("unused") // Used in Swift
suspend fun awaitFirebaseOptionsForIOS(): FirebaseIOSOptionsBridge? {
  if (FirebaseConfigProvider.currentCachedConfig() == null) {
    try {
      getStoredFirebaseConfig()?.let { (json, at) ->
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - at < FirebaseConfigProvider.TTL_MS) {
          val restored: FirebaseConfig.IOS = globalSerializer.decodeFromString(json)
          FirebaseConfigProvider.setCachedConfig(restored, FirebasePlatform.IOS, at)
        }
      }
    } catch (e: Exception) {
      NSLog("awaitFirebaseOptionsForIOS: cache restore failed: $e")
    }
  }

  val settings = getSettingsFromKeychain()
  val cached = FirebaseConfigProvider.currentCachedConfig() as? FirebaseConfig.IOS

  if (settings.token == null) {
    return cached?.let(::FirebaseIOSOptionsBridge)
  }

  return try {
    val config = FirebaseConfigProvider.getConfig(FirebasePlatform.IOS, "${settings.apiEndpoint}/api")
    (config as? FirebaseConfig.IOS)?.let(::FirebaseIOSOptionsBridge)
  } catch (e: Exception) {
    NSLog("awaitFirebaseOptionsForIOS failed: $e")
    cached?.let(::FirebaseIOSOptionsBridge)
  }
}

/**
 * Plain data holder for the seven fields that iOS `FIROptions` needs. Kept as a
 * separate class (rather than exposing [FirebaseConfig.IOS] directly) so Swift doesn't
 * have to know about the sealed hierarchy or Kotlin serialization annotations.
 */
data class FirebaseIOSOptionsBridge(
  val apiKey: String,
  val googleAppId: String,
  val gcmSenderId: String,
  val projectId: String,
  val storageBucket: String?,
  val bundleId: String,
  val clientId: String?
) {
  constructor(config: FirebaseConfig.IOS) : this(
    apiKey = config.apiKey,
    googleAppId = config.googleAppId,
    gcmSenderId = config.gcmSenderId,
    projectId = config.projectId,
    storageBucket = config.storageBucket,
    bundleId = config.bundleId,
    clientId = config.clientId
  )
}

fun handleDeepLink(latestDeepLink: String?) {
  val url = latestDeepLink?.let { Url(it) } ?: return

  val path = if (url.protocol.name == "interfold") {
    if (url.host.isNotEmpty()) {
      "/" + url.host + url.encodedPath
    } else {
      url.encodedPath
    }
  } else {
    url.encodedPath
  }

  when (path) {
    "/auth/token", "/deep/auth/token" -> {
      platformLog("/deep/auth/token hit!")
      url.parameters["token"]?.let {
        platformLog("Token received: $it")
        platformEventFlow.tryEmit(PlatformEvent.LoginTokenReceived(it))
      }
    }

    "/link_success/discord", "/deep/link_success/discord" -> {
      platformLog("/deep/link_success/discord hit!")
      platformEventFlow.tryEmit(PlatformEvent.ExternallyHandleable.DiscordAccountLinked)
    }

    "/link_success/google", "/deep/link_success/google" -> {
      platformLog("/deep/link_success/google hit!")
      platformEventFlow.tryEmit(PlatformEvent.ExternallyHandleable.GoogleAccountLinked)
    }

    "/link_success/apple", "/deep/link_success/apple" -> {
      platformLog("/deep/link_success/apple hit!")
      platformEventFlow.tryEmit(PlatformEvent.ExternallyHandleable.AppleAccountLinked)
    }
  }
}