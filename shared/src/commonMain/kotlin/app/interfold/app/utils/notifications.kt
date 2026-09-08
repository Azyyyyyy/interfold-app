package app.interfold.app.utils

import androidx.compose.runtime.Composable
import app.interfold.app.ui.model.interfaces.ApiInterface
import app.interfold.app.ui.model.interfaces.SettingsInterface

internal typealias InitPushNotificationsCallbacks = Pair<(
  token: String,
  api: ApiInterface,
  settings: SettingsInterface,
  platformUtilities: PlatformUtilities
) -> Unit, (
  showPushNotifications: Boolean,
  api: ApiInterface,
  settings: SettingsInterface,
  platformUtilities: PlatformUtilities
) -> Unit>

@Composable
internal expect fun InitPushNotifications(): InitPushNotificationsCallbacks