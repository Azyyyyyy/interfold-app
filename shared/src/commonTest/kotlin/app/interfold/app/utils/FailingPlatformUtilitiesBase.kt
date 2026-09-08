package app.interfold.app.utils

import app.interfold.app.Settings

/**
 * Shared scaffolding for the per-platform [FailingPlatformUtilities] actuals.
 * Implements every behavioural method on [CommonPlatformUtilities] with a
 * single loud-fail tripwire so any unexpected call from a code path under test
 * surfaces immediately with a clear, actionable message instead of silently
 * succeeding or no-op-ing.
 *
 * Each platform actual extends this and only adds members its own
 * `actual interface PlatformUtilities` declares beyond [CommonPlatformUtilities]
 * (e.g. `context: Context` on Android, `injectedPlatformDelegate` on iOS).
 */
abstract class FailingPlatformUtilitiesBase : CommonPlatformUtilities {
  private fun unsupported(): Nothing = error(
    "FailingPlatformUtilities does not support PlatformUtilities calls in commonTest. " +
      "If a unit test legitimately needs one, introduce a narrower seam (analogous to " +
      "PhoenixSocketSessionFactory for ApiInterfaceImpl) and inject a behaviour-modelled fake."
  )

  // Unit-returning methods use block bodies so the actual's return type stays
  // Unit (matching the expect/interface signature). Expression-body `= unsupported()`
  // would infer `Nothing`, which Kotlin rejects as an actual/expect mismatch.
  override fun exitApplication(exitApplicationType: ExitApplicationType) { unsupported() }
  override fun saveSettings(settings: Settings) { unsupported() }
  override fun clearServerConfigCache() { unsupported() }
  override fun showAlert(message: String) { unsupported() }
  override suspend fun recoveryCodeToJWE(recoveryCode: String, settings: Settings): String =
    unsupported()
  override suspend fun generateRecoveryCode(settings: Settings): Pair<String, String> =
    unsupported()
  override suspend fun setupEncryptionKey(encryptionKey: String): Settings? = unsupported()
  override suspend fun getEncryptionKey(settings: Settings): String = unsupported()
  override fun decryptEncryptionKey(encryptedEncryptionKey: String): String = unsupported()
  override suspend fun encryptData(data: String, settings: Settings): String = unsupported()
  override suspend fun decryptData(data: String, settings: Settings): String = unsupported()
  override fun openURL(
    url: String,
    colorSchemeParams: ColorSchemeParams,
    webURLOpenBehavior: WebURLOpenBehavior,
  ) { unsupported() }
  override fun updateWidgets(sessionInvalidated: Boolean) { unsupported() }
  override fun performAdditionalPushNotificationSetup() { unsupported() }
  override suspend fun reinitPushNotifications(settings: Settings) { unsupported() }
}
