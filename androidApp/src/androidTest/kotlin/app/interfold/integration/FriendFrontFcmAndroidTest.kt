package app.interfold.integration

import android.Manifest
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import app.interfold.FirebaseInitializer
import app.interfold.app.utils.LocalNetworkAccess
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Android receive-path IT: B is this device, with a real FCM token.
 * Pass is a tray notification whose title/body match the FCM payload.
 */
@RunWith(AndroidJUnit4::class)
class FriendFrontFcmAndroidTest {

  @get:Rule
  val runtimePermissions: GrantPermissionRule = GrantPermissionRule.grant(
    *buildList {
      if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
      if (Build.VERSION.SDK_INT >= 37) add(LocalNetworkAccess.PERMISSION)
    }.toTypedArray(),
  )

  @Test
  fun friendFront_showsSystemNotification() = runBlocking {
    val env = loadAndroidItEnvOrNull()
    assumeTrue(
      "Skip: Android FCM IT needs :androidApp:androidDeviceIntegrationTest (backend URL + tokens).",
      env != null,
    )
    val loaded = env!!
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    grantLocalNetworkAccess(instrumentation, instrumentation.context.packageName)
    grantLocalNetworkAccess(instrumentation, context.packageName)
    val lanTest = context.packageManager.checkPermission(
      LocalNetworkAccess.PERMISSION,
      instrumentation.context.packageName,
    )
    val lanApp = context.packageManager.checkPermission(
      LocalNetworkAccess.PERMISSION,
      context.packageName,
    )
    val baseUrl = try {
      resolveReachableBackendUrl(loaded.baseUrl)
    } catch (e: Throwable) {
      throw IllegalStateException(
        "${e.message} testPkg=${instrumentation.context.packageName} lanTest=$lanTest " +
          "appPkg=${context.packageName} lanApp=$lanApp",
        e,
      )
    }
    val resolved = loaded.copy(baseUrl = baseUrl)
    val hasPlay = try {
      context.packageManager.getPackageInfo("com.google.android.gms", 0)
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
    assumeTrue(
      "Skip: emulator/device has no Play Services package (com.google.android.gms).",
      hasPlay,
    )

    val config = fetchAndroidFirebaseConfigOrNull(resolved.baseUrl)
    assumeTrue(
      "Skip: GET /api/settings/firebase-config?platform=android returned no Android config. " +
        "Seed ~/.interfold/it-fcm-android.json via scripts/setup-it-firebase.ps1.",
      config != null,
    )
    val androidConfig = config!!
    check(!androidConfig.stringOrNull("api_key").isNullOrBlank() || !androidConfig.stringOrNull("apiKey").isNullOrBlank()) {
      "checkpoint firebase-config: Android config is missing api_key"
    }

    LocalNetworkAccess.noteContext(context)
    LocalNetworkAccess.ensureGateInstalled()

    val initialized = FirebaseInitializer.fetchPersistAndInitialize(
      context,
      "${resolved.baseUrl}/api",
    )
    check(initialized) {
      "checkpoint FirebaseApp: fetchPersistAndInitialize failed for ${resolved.baseUrl}"
    }

    val fcmToken = try {
      withTimeout(20.seconds) { FirebaseMessaging.getInstance().token.await() }
    } catch (e: Throwable) {
      throw IllegalStateException("checkpoint FCM token: FirebaseMessaging.getInstance().token failed: $e", e)
    }
    check(fcmToken.isNotBlank()) { "checkpoint FCM token: empty token" }

    val suffix = Clock.System.now().toEpochMilliseconds().toString(36)
    val aliceName = "alice-$suffix"
    val bobName = "bob-$suffix"
    val alterName = "Kai-$suffix"

    ensureUserExists(resolved.baseUrl, resolved.aliceToken, aliceName)
    ensureUserExists(resolved.baseUrl, resolved.bobToken, bobName)
    sendFriendRequestHttp(resolved.baseUrl, resolved.bobToken, "id:${resolved.aliceSystemId}")
    acceptFriendRequestHttp(resolved.baseUrl, resolved.aliceToken, "id:${resolved.bobSystemId}")
    registerPushToken(resolved.baseUrl, resolved.bobToken, fcmToken)

    val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notifications.cancelAll()

    val alter = createAlterHttp(resolved.baseUrl, resolved.aliceToken, alterName)
    setAlterFriendsOnly(resolved.baseUrl, resolved.aliceToken, alter.id)
    startFrontHttp(resolved.baseUrl, resolved.aliceToken, alter.id)

    val send = awaitFcmSend(resolved.baseUrl, resolved.aliceToken, resolved.aliceSystemId)
    check(send.skipReason == null) {
      "checkpoint Firebase send: skipped (${send.skipReason}) title=${send.title} " +
        "success=${send.successCount} errors=${send.errorCodes}"
    }
    check(send.successCount > 0) {
      "checkpoint Firebase send: success=${send.successCount} failure=${send.failureCount} " +
        "errors=${send.errorCodes} title=${send.title}"
    }

    val expectedTitle = "Front update: $aliceName"
    val deadline = Clock.System.now() + 25.seconds
    var last = ""
    while (Clock.System.now() < deadline) {
      last = snapshotNotifications(notifications)
      if (notificationShown(notifications, expectedTitle, alterName)) return@runBlocking
      delay(250.milliseconds)
    }
    error(
      "checkpoint notification displayed: no tray notification with " +
        "title='$expectedTitle' body containing '$alterName'. shown=$last",
    )
  }
}

private fun snapshotNotifications(manager: NotificationManager): String =
  manager.activeNotifications.joinToString(prefix = "[", postfix = "]") { posted ->
    val extras = posted.notification.extras
    val title = extras.getString(Notification.EXTRA_TITLE).orEmpty()
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    "$title|$text"
  }

private fun notificationShown(
  manager: NotificationManager,
  title: String,
  bodyPart: String,
): Boolean =
  manager.activeNotifications.any { posted ->
    val extras = posted.notification.extras
    val shownTitle = extras.getString(Notification.EXTRA_TITLE).orEmpty()
    val shownText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    shownTitle == title && shownText.contains(bodyPart)
  }

private fun grantLocalNetworkAccess(instrumentation: Instrumentation, packageName: String) {
  if (Build.VERSION.SDK_INT < 37) return
  val automation = instrumentation.uiAutomation
  runCatching {
    automation.grantRuntimePermission(packageName, LocalNetworkAccess.PERMISSION)
  }
  listOf(
    "pm grant $packageName ${LocalNetworkAccess.PERMISSION}",
    "appops set $packageName LOCAL_NETWORK allow",
    "appops set $packageName android:local_network allow",
    "cmd appops set $packageName LOCAL_NETWORK allow",
  ).forEach { command ->
    runCatching { automation.executeShellCommand(command).close() }
  }
}
