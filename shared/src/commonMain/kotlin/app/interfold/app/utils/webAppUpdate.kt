package app.interfold.app.utils

import kotlinx.coroutines.flow.Flow

/**
 * Build id of a service worker that is newer than the JS/WASM this page
 * loaded. Empty on Android, iOS, and desktop. Wasm emits a value whenever
 * the controlling worker's stamped version differs from
 * [app.interfold.app.BuildInfo.BUILD_ID], including after the snackbar is
 * dismissed so Settings can still offer Reload.
 */
expect val pendingWebAppVersion: Flow<String?>

/** True after the user dismisses the snackbar for the current pending version. */
expect val webAppUpdateNoticeDismissed: Flow<Boolean>

expect fun reloadWebApp()

expect fun dismissPendingWebAppUpdate()

internal fun isPendingWebAppVersion(
  runningBuildId: String,
  incomingVersion: String?
): Boolean {
  return !incomingVersion.isNullOrEmpty() && incomingVersion != runningBuildId
}

internal fun shouldShowWebAppUpdateNotice(
  runningBuildId: String,
  incomingVersion: String?,
  dismissedVersion: String?
): Boolean {
  if (!isPendingWebAppVersion(runningBuildId, incomingVersion)) return false
  if (incomingVersion == dismissedVersion) return false
  return true
}
