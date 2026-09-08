package app.interfold

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.interfold.app.utils.FirebaseConfig
import app.interfold.app.utils.FirebaseConfigProvider
import app.interfold.app.utils.FirebasePlatform
import app.interfold.app.utils.globalSerializer
import app.interfold.util.createSharedPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString

/**
 * Runtime replacement for the `com.google.gms.google-services` Gradle plugin. The
 * plugin previously turned `google-services.json` into an Android resource and let the
 * `FirebaseInitProvider` ContentProvider auto-initialise `FirebaseApp` at app boot;
 * now we pull the same `FirebaseOptions` shape from the API server via
 * [FirebaseConfigProvider], persist it in SharedPreferences, and initialise
 * `FirebaseApp` ourselves on demand.
 */
object FirebaseInitializer {
  private const val TAG = "InterfoldFirebaseInit"

  const val PREF_FIREBASE_CONFIG = "FIREBASE_CONFIG_JSON"
  const val PREF_FIREBASE_CONFIG_AT = "FIREBASE_CONFIG_AT"

  /**
   * Idempotent, synchronous. If `FirebaseApp` is already initialised, does nothing and
   * returns true. Otherwise attempts to read a previously persisted config from
   * SharedPreferences and initialise from it. Returns true iff `FirebaseApp` is
   * initialised after the call.
   *
   * Safe to call from anywhere: MainActivity.onCreate, service onCreate, background
   * workers. The SharedPreferences read is EncryptedSharedPreferences-backed, so
   * expect a small amount of one-time crypto init the first time it runs in the
   * process.
   */
  fun ensureInitialized(context: Context): Boolean {
    if (FirebaseApp.getApps(context).isNotEmpty()) return true

    val prefs = createSharedPreferences(context)
    val json = prefs.getString(PREF_FIREBASE_CONFIG, null) ?: return false
    val atMillis = prefs.getLong(PREF_FIREBASE_CONFIG_AT, 0L)

    val config: FirebaseConfig.Android = try {
      globalSerializer.decodeFromString(json)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode cached Firebase config: $e")
      return false
    }

    try {
      runBlocking {
        FirebaseConfigProvider.setCachedConfig(config, FirebasePlatform.ANDROID, atMillis)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to seed FirebaseConfigProvider cache: $e")
    }

    return initializeFromConfig(context, config)
  }

  /**
   * Fetch fresh config from the server (using the in-memory cache if it's still within
   * [FirebaseConfigProvider.TTL_MS]), persist it, and ensure `FirebaseApp` is
   * initialised. Called from `MainActivity.onCreate`.
   */
  suspend fun fetchPersistAndInitialize(context: Context, endpoint: String): Boolean {
    return try {
      val config = FirebaseConfigProvider.getConfig(FirebasePlatform.ANDROID, endpoint)
      if (config !is FirebaseConfig.Android) {
        Log.e(TAG, "Expected Android Firebase config but got ${config::class.simpleName}")
        return ensureInitialized(context)
      }
      val at = System.currentTimeMillis()
      val json = globalSerializer.encodeToString(config)
      createSharedPreferences(context).edit(commit = true) {
        putString(PREF_FIREBASE_CONFIG, json)
        putLong(PREF_FIREBASE_CONFIG_AT, at)
      }
      initializeFromConfig(context, config)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch+persist Firebase config: $e")
      ensureInitialized(context)
    }
  }

  /**
   * Remove the persisted Firebase config from EncryptedSharedPreferences. Called when
   * the API endpoint changes so that a subsequent cold start won't restore config that
   * was fetched from the previous server. Does not tear down the live `FirebaseApp`
   * singleton — that's handled by `MainActivity.platformUtilities.reinitPushNotifications`,
   * which fires from `SettingsInterfaceImpl.setToken` on login completion (see the
   * `wasLoggedOut && token != null` branch there).
   */
  fun clearOnDisk(context: Context) {
    try {
      createSharedPreferences(context).edit(commit = true) {
        remove(PREF_FIREBASE_CONFIG)
        remove(PREF_FIREBASE_CONFIG_AT)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear persisted Firebase config: $e")
    }
  }

  private fun initializeFromConfig(context: Context, config: FirebaseConfig.Android): Boolean {
    if (FirebaseApp.getApps(context).isNotEmpty()) return true
    return try {
      val builder = FirebaseOptions.Builder()
        .setApiKey(config.apiKey)
        .setApplicationId(config.applicationId)
        .setProjectId(config.projectId)
        .setGcmSenderId(config.gcmSenderId)
      config.storageBucket?.let { builder.setStorageBucket(it) }
      FirebaseApp.initializeApp(context, builder.build())
      true
    } catch (e: Exception) {
      Log.e(TAG, "FirebaseApp.initializeApp failed: $e")
      false
    }
  }
}
