package app.interfold.app.integration

import app.interfold.app.utils.installShownNotificationLatch
import app.interfold.app.utils.shownNotificationsSnapshot
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wasm receive-path IT: B is a browser client that acquired a real web FCM
 * token. Pass is `Notification` / `showNotification` with the FCM title and
 * body, not in-app friend state.
 */
class FriendFrontFcmWasmTest {

  @Test
  fun friendFront_showsBrowserNotification() =
    runBackendIntegrationTest(timeout = 90.seconds, ::assertFriendFrontShowsNotification)
}

internal suspend fun assertFriendFrontShowsNotification(baseUrl: String) {
  val config = fetchWebFirebaseConfigOrNull(baseUrl)
  check(config != null) {
    "checkpoint firebase-config: GET /api/settings/firebase-config?platform=web did not return web config. " +
      "Seed $FCM_WEB_ENV (with vapid_key) on the backend process."
  }
  check(!config.stringOrNull("vapid_key").isNullOrBlank() || !config.stringOrNull("vapidKey").isNullOrBlank()) {
    "checkpoint firebase-config: web config is missing vapid_key"
  }

  val suffix = Clock.System.now().toEpochMilliseconds().toString(36)
  val aliceName = "alice-$suffix"
  val bobName = "bob-$suffix"
  val alterName = "Kai-$suffix"

  val alice = injectedFronterPrincipal()
    ?: error(
      "checkpoint fronter token: wasm IT needs a second JWT (backend-token-a.txt). " +
        "Re-run :shared:wasmJsBrowserIntegrationTest so BackendEntryPoint mints both principals.",
    )
  val bob = obtainTestToken(baseUrl, uid = "bob-$suffix")
  ensureUserExists(baseUrl, alice.token, username = aliceName)
  ensureUserExists(baseUrl, bob.token, username = bobName)

  sendFriendRequestHttp(baseUrl, bob.token, "id:${alice.scopedSystemId}")
  acceptFriendRequestHttp(baseUrl, alice.token, "id:${bob.scopedSystemId}")

  IntegrationHarness(baseUrl, bob.token).use { browser ->
    browser.loadAndAwaitInit()
    unregisterServiceWorkersForFcmTest().await<JsAny?>()
    installShownNotificationLatch()
    enableWebPushFromSettings(browser)

    val alter = createAlterHttp(baseUrl, alice.token, alterName)
    setAlterFriendsOnly(baseUrl, alice.token, alter.id)
    startFrontHttp(baseUrl, alice.token, alter.id)

    val expectedTitle = "Front update: $aliceName"
    val deadline = Clock.System.now() + 25.seconds
    var last = ""
    while (Clock.System.now() < deadline) {
      last = shownNotificationsSnapshot()
      if (notificationShown(last, expectedTitle, alterName)) return
      delay(250.milliseconds)
    }
    error(
      "checkpoint notification displayed: no Notification/showNotification with " +
        "title='$expectedTitle' body containing '$alterName'. shown=$last",
    )
  }
}

private fun notificationShown(snapshot: String, title: String, bodyPart: String): Boolean {
  val arr = runCatching { Json.parseToJsonElement(snapshot).jsonArray }.getOrNull() ?: return false
  return arr.any { el ->
    val obj = el.jsonObject
    val shownTitle = obj["title"]?.jsonPrimitive?.content.orEmpty()
    val shownBody = obj["body"]?.jsonPrimitive?.content.orEmpty()
    shownTitle == title && shownBody.contains(bodyPart)
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "() => navigator.serviceWorker.getRegistrations()" +
    ".then(function (regs) { return Promise.all(regs.map(function (r) { return r.unregister(); })); })",
)
private external fun unregisterServiceWorkersForFcmTest(): Promise<JsAny?>
