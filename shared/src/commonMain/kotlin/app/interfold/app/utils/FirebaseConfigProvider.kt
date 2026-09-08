package app.interfold.app.utils

import app.interfold.app.Settings
import app.interfold.app.api.fetchAndroidFirebaseConfig
import app.interfold.app.api.fetchIOSFirebaseConfig
import app.interfold.app.api.fetchWebFirebaseConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/**
 * Which Firebase client configuration shape to request from the server. Each platform
 * fetches only its own shape.
 */
enum class FirebasePlatform(val internalName: String) {
  ANDROID("android"),
  IOS("ios"),
  WEB("web")
}

/**
 * Sealed hierarchy of platform-specific Firebase client configurations. Each variant
 * contains exactly the fields needed to construct the corresponding native
 * `FirebaseOptions` / `firebase.initializeApp` config.
 *
 * None of these values are credentials — they only identify the Firebase project the
 * device SDK should talk to. The credential that actually sends pushes is a service
 * account key held by the API server and never distributed to clients.
 */
sealed class FirebaseConfig {
  @Serializable
  data class Android(
    @SerialName("api_key") val apiKey: String,
    @SerialName("application_id") val applicationId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("gcm_sender_id") val gcmSenderId: String,
    @SerialName("storage_bucket") val storageBucket: String? = null
  ) : FirebaseConfig()

  @Suppress("EnumEntryName")
  @Serializable
  data class IOS(
    @SerialName("api_key") val apiKey: String,
    @SerialName("google_app_id") val googleAppId: String,
    @SerialName("gcm_sender_id") val gcmSenderId: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("storage_bucket") val storageBucket: String? = null,
    @SerialName("bundle_id") val bundleId: String,
    @SerialName("client_id") val clientId: String? = null
  ) : FirebaseConfig()

  @Serializable
  data class Web(
    @SerialName("api_key") val apiKey: String,
    @SerialName("auth_domain") val authDomain: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("storage_bucket") val storageBucket: String? = null,
    @SerialName("messaging_sender_id") val messagingSenderId: String,
    @SerialName("app_id") val appId: String,
    @SerialName("vapid_key") val vapidKey: String
  ) : FirebaseConfig()
}

/**
 * Mirrors [PublicKeyProvider]: in-memory cache with mutex + TTL, network fetch, and a
 * restore hook so each platform can seed the cache from its own persistent store
 * (SharedPreferences on Android, KVault on iOS, localStorage on wasm).
 */
object FirebaseConfigProvider {
  private var cachedConfig: FirebaseConfig? = null
  private var cachedPlatform: FirebasePlatform? = null
  private var cachedAt: Long = 0
  private val mutex = Mutex()

  /** 7 days — Firebase client config rotates infrequently and is not a secret. */
  const val TTL_MS: Long = 7L * 24 * 60 * 60 * 1000

  /** Hard ceiling on any single fetch, so a hung socket can't leak a coroutine. */
  const val NETWORK_TIMEOUT_MS: Long = 15_000L

  suspend fun getConfig(
    platform: FirebasePlatform,
    endpoint: String = Settings.DEFAULT_API_ENDPOINT + "/api"
  ): FirebaseConfig {
    mutex.withLock {
      val now = Clock.System.now().toEpochMilliseconds()
      val cached = cachedConfig
      if (cached != null && cachedPlatform == platform && (now - cachedAt) < TTL_MS) {
        return cached
      }

      try {
        val response = withTimeout(NETWORK_TIMEOUT_MS) {
          when (platform) {
            FirebasePlatform.ANDROID -> fetchAndroidFirebaseConfig(endpoint)
            FirebasePlatform.IOS -> fetchIOSFirebaseConfig(endpoint)
            FirebasePlatform.WEB -> fetchWebFirebaseConfig(endpoint)
          }
        }

        val data = response.data
        if (data != null) {
          cachedConfig = data
          cachedPlatform = platform
          cachedAt = now
          return data
        } else {
          throw Exception(response.error ?: "Empty Firebase config response")
        }
      } catch (e: Exception) {
        if (cached != null && cachedPlatform == platform) return cached
        throw e
      }
    }
  }

  /** Non-suspending accessor for whatever is currently cached (may be null). */
  fun currentCachedConfig(): FirebaseConfig? = cachedConfig

  /** Non-suspending accessor for the platform of the currently cached config. */
  fun currentCachedPlatform(): FirebasePlatform? = cachedPlatform

  fun clearCache() {
    cachedConfig = null
    cachedPlatform = null
    cachedAt = 0
  }

  /**
   * Seed the cache from a platform-specific persistent store. Callers should verify
   * the persisted `cachedAtMillis` is still within [TTL_MS] before restoring.
   */
  suspend fun setCachedConfig(
    config: FirebaseConfig,
    platform: FirebasePlatform,
    cachedAtMillis: Long
  ) {
    mutex.withLock {
      cachedConfig = config
      cachedPlatform = platform
      cachedAt = cachedAtMillis
    }
  }
}
