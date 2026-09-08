package app.interfold.app.utils

import app.interfold.app.api.fetchPublicKey
import app.interfold.app.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock

object PublicKeyProvider {
  private var cachedKey: String? = null
  private var cachedAt: Long = 0
  private val mutex = Mutex()

  // default TTL 28 days
  const val TTL_MS: Long = 24 * 28 * 60 * 60 * 1000

  /** Hard ceiling on any single fetch, so a hung socket can't leak a coroutine. */
  const val NETWORK_TIMEOUT_MS: Long = 15_000L

  suspend fun getPublicKey(endpoint: String = Settings.DEFAULT_API_ENDPOINT + "/api"): String {
    mutex.withLock {
      val now = Clock.System.now().toEpochMilliseconds()
      if (cachedKey != null && (now - cachedAt) < TTL_MS) {
        return cachedKey!!
      }

      try {
        val response = withTimeout(NETWORK_TIMEOUT_MS) { fetchPublicKey(endpoint) }
        if (response.data != null && response.data.isNotBlank()) {
          cachedKey = response.data
          cachedAt = now
          return cachedKey!!
        } else {
          throw Exception(response.error ?: "Empty public key response")
        }
      } catch (e: Exception) {
        if (cachedKey != null) return cachedKey!!
        throw e
      }
    }
  }

  fun clearCache() {
    cachedKey = null
    cachedAt = 0
  }

  /**
   * Set a cached key and timestamp from a platform-specific persistent store.
   * This is useful for restoring a previously persisted public key on startup.
   */
  suspend fun setCachedKey(key: String, cachedAtMillis: Long) {
    mutex.withLock {
      cachedKey = key
      cachedAt = cachedAtMillis
    }
  }
}
