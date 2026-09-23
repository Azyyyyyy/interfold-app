package app.interfold.app.utils

import kotlinx.coroutines.flow.Flow

/**
 * Build id of a service worker that is newer than the JS/WASM this page
 * loaded. Empty on Android, iOS, and desktop. Wasm emits a value only when
 * the controlling worker's stamped version differs from [app.interfold.app.BuildInfo.BUILD_ID].
 */
expect val pendingWebAppVersion: Flow<String?>

expect fun reloadWebApp()

expect fun dismissPendingWebAppUpdate()

internal fun shouldAnnounceWebAppUpdate(
  runningBuildId: String,
  incomingVersion: String?,
  dismissedVersion: String?
): Boolean {
  if (incomingVersion.isNullOrEmpty()) return false
  if (incomingVersion == runningBuildId) return false
  if (incomingVersion == dismissedVersion) return false
  return true
}
