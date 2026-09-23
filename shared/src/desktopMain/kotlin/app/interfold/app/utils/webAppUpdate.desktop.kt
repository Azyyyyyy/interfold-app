package app.interfold.app.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual val pendingWebAppVersion: Flow<String?> = flowOf(null)

actual val webAppUpdateNoticeDismissed: Flow<Boolean> = flowOf(false)

actual fun reloadWebApp() = Unit

actual fun dismissPendingWebAppUpdate() = Unit
