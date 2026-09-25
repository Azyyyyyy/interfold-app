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

/** Hide the snackbar and keep the running build across reloads until Reload. */
expect fun dismissPendingWebAppUpdate()

internal fun isPendingWebAppVersion(
  runningBuildId: String,
  incomingVersion: String?
): Boolean = AppUpdatePolicy.isPending(runningBuildId, incomingVersion)

internal fun shouldShowWebAppUpdateNotice(
  runningBuildId: String,
  incomingVersion: String?,
  dismissedVersion: String?
): Boolean = AppUpdatePolicy.shouldShowNotice(runningBuildId, incomingVersion, dismissedVersion)
