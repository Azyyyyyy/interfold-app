package app.interfold.app.utils

import androidx.compose.runtime.Composable
import app.interfold.app.ui.model.interfaces.ApiInterface
import app.interfold.app.ui.model.interfaces.SettingsInterface

// Stub declaration
@Composable
internal actual fun InitPushNotifications(): InitPushNotificationsCallbacks = { _: String, _: ApiInterface, _: SettingsInterface, _: PlatformUtilities -> } to { _, _, _, _ -> }