package app.interfold.app.utils

/**
 * Platform-agnostic rules for a deferred app update.
 *
 * Wasm uses this for the PWA notice. The Kotlin/JS service worker
 * (`:pwa-service-worker-js`) uses [servingCacheAfterActivate] on activate
 * and apply. Other platforms can later reuse [onIncomingVersion] /
 * [dismissNotice] / [requestApply] without the cache names.
 */
object AppUpdatePolicy {
  const val APP_CACHE_PREFIX = "interfold-app-cache-"
  const val FIREBASE_CONFIG_CACHE = "interfold-firebase-config-v1"
  const val SW_META_CACHE = "interfold-sw-meta-v1"

  fun appCacheName(version: String): String = "$APP_CACHE_PREFIX$version"

  fun isPending(runningBuildId: String, incomingVersion: String?): Boolean {
    return !incomingVersion.isNullOrEmpty() && incomingVersion != runningBuildId
  }

  fun shouldShowNotice(
    runningBuildId: String,
    incomingVersion: String?,
    dismissedVersion: String?
  ): Boolean {
    if (!isPending(runningBuildId, incomingVersion)) return false
    if (incomingVersion == dismissedVersion) return false
    return true
  }

  fun onIncomingVersion(
    runningBuildId: String,
    incomingVersion: String?,
    dismissedVersion: String?
  ): IncomingVersionEffect {
    return if (isPending(runningBuildId, incomingVersion)) {
      IncomingVersionEffect(
        pendingVersion = incomingVersion,
        noticeDismissed = !shouldShowNotice(runningBuildId, incomingVersion, dismissedVersion),
      )
    } else {
      IncomingVersionEffect(pendingVersion = null, noticeDismissed = false)
    }
  }

  /**
   * Last-tab reload can activate a new worker without [requestApply].
   * Keep the previous cache unless apply was requested for this version,
   * or there is no previous cache (first install).
   */
  fun servingCacheAfterActivate(
    currentCacheName: String,
    currentVersion: String,
    applyRequestedVersion: String?,
    previousCacheNames: List<String>,
    pinnedCacheName: String?,
    hasAppShell: (String) -> Boolean = { true },
  ): ServingCacheDecision {
    if (applyRequestedVersion == currentVersion || previousCacheNames.isEmpty()) {
      return ServingCacheDecision(servingCacheName = currentCacheName, applied = true)
    }
    val serving = resolvePreviousServingCache(previousCacheNames, pinnedCacheName, hasAppShell)
      ?: currentCacheName
    return ServingCacheDecision(servingCacheName = serving, applied = false)
  }

  fun resolvePreviousServingCache(
    previousCacheNames: List<String>,
    pinnedCacheName: String?,
    hasAppShell: (String) -> Boolean,
  ): String? {
    if (pinnedCacheName != null && pinnedCacheName in previousCacheNames) return pinnedCacheName
    return previousCacheNames.firstOrNull(hasAppShell) ?: previousCacheNames.firstOrNull()
  }

  /** Pinned to an older cache: do not fall through to the network for JS/WASM. */
  fun mayFetchAppBinaryFromNetwork(servingCacheName: String, currentCacheName: String): Boolean {
    return servingCacheName == currentCacheName
  }

  fun isReservedCache(name: String): Boolean {
    return name == FIREBASE_CONFIG_CACHE || name == SW_META_CACHE
  }

  fun cachesToDeleteOnApply(allCacheNames: List<String>, currentCacheName: String): List<String> {
    return allCacheNames.filter { it != currentCacheName && !isReservedCache(it) }
  }
}

data class IncomingVersionEffect(
  val pendingVersion: String?,
  val noticeDismissed: Boolean,
)

data class ServingCacheDecision(
  val servingCacheName: String,
  val applied: Boolean,
)

/**
 * In-memory session for the update notice and apply flag.
 * Wasm persists [dismissedVersion] / [applyRequestedVersion] in storage;
 * other platforms can swap those later without changing the rules.
 */
class AppUpdateSession(
  val runningBuildId: String,
) {
  var pendingVersion: String? = null
    private set
  var noticeDismissed: Boolean = false
    private set
  var dismissedVersion: String? = null
    private set
  var applyRequestedVersion: String? = null
    private set

  val settingsReloadVisible: Boolean
    get() = pendingVersion != null

  val snackbarVisible: Boolean
    get() = pendingVersion != null && !noticeDismissed

  fun onIncomingVersion(incomingVersion: String?) {
    val effect = AppUpdatePolicy.onIncomingVersion(
      runningBuildId,
      incomingVersion,
      dismissedVersion,
    )
    pendingVersion = effect.pendingVersion
    noticeDismissed = effect.noticeDismissed
  }

  fun dismissNotice() {
    val version = pendingVersion ?: return
    dismissedVersion = version
    noticeDismissed = true
  }

  fun requestApply() {
    val version = pendingVersion ?: return
    applyRequestedVersion = version
  }

  fun servingAfterActivate(
    previousCacheNames: List<String>,
    pinnedCacheName: String? = previousCacheNames.firstOrNull(),
    hasAppShell: (String) -> Boolean = { true },
  ): ServingCacheDecision {
    return AppUpdatePolicy.servingCacheAfterActivate(
      currentCacheName = AppUpdatePolicy.appCacheName(pendingVersion ?: runningBuildId),
      currentVersion = pendingVersion ?: runningBuildId,
      applyRequestedVersion = applyRequestedVersion,
      previousCacheNames = previousCacheNames,
      pinnedCacheName = pinnedCacheName,
      hasAppShell = hasAppShell,
    )
  }
}
