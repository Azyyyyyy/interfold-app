package app.interfold.app.integration

import app.interfold.app.api.updatePushNotificationToken
import app.interfold.app.utils.acquireWebFCMToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Same path as Settings → Show push notifications: [setShowPushNotifications]
 * → [acquireWebFCMToken] → [provideFirebaseMessagingToken] → commit(true) →
 * [updatePushNotificationToken].
 */
internal suspend fun enableWebPushFromSettings(harness: IntegrationHarness) {
  val done = CompletableDeferred<Result<Unit>>()
  harness.settings.setShowPushNotifications(
    showPushNotifications = true,
    showAlert = {},
    sendToken = { harness.api.updatePushNotificationToken() },
    invalidateToken = { harness.api.invalidatePushNotificationToken() },
    tryInit = { commit ->
      harness.scope.launch(Dispatchers.Main) {
        try {
          val grant = ensureKarmaNotificationGrant()
          val token = try {
            acquireWebFCMToken(
              endpoint = "${harness.settings.data.value.apiEndpoint}/api",
              requestPermissionIfNeeded = true,
            )
          } catch (e: Throwable) {
            throw IllegalStateException("${e.message} grant=$grant", e)
          }
          check(token.isNotBlank()) { "checkpoint web FCM token: empty token" }
          harness.api.provideFirebaseMessagingToken(token)
          updatePushNotificationToken(
            "${harness.settings.data.value.apiEndpoint}/api",
            harness.token,
            token,
          )
          commit(true)
          done.complete(Result.success(Unit))
        } catch (e: Throwable) {
          commit(false)
          done.complete(Result.failure(e))
        }
      }
    },
  )
  withTimeout(45.seconds) { done.await() }.getOrThrow()
  check(harness.settings.data.value.showPushNotifications) {
    "checkpoint settings: showPushNotifications is still false after tryInit"
  }
}
