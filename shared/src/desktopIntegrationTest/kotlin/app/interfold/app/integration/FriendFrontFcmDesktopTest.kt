package app.interfold.app.integration

import app.interfold.app.api.ChannelMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop send-path IT: two clients become friends, A fronts, the API's FCM
 * outbox records a `fronting_changed` multicast that includes B. This does
 * not claim a notification was shown (JVM cannot receive FCM).
 */
class FriendFrontFcmDesktopTest {

  @Test
  fun friendFront_sendsFcmToFriend() {
    assumeTrue(
      !interfoldItFcmServiceAccountJson().isNullOrBlank(),
      "Skip: $FCM_SA_ENV is not set. Seed ~/.interfold/it-fcm-sa.json via scripts/setup-it-firebase.ps1.",
    )
    runBackendIntegrationTest(timeout = 90.seconds, ::assertFriendFrontSendsFcm)
  }
}

internal suspend fun assertFriendFrontSendsFcm(baseUrl: String) {
  val suffix = Clock.System.now().toEpochMilliseconds().toString(36)
  val aliceName = "alice-$suffix"
  val bobName = "bob-$suffix"
  val alterName = "Kai-$suffix"

  val alicePrincipal = obtainTestToken(baseUrl, uid = "alice-$suffix")
  val bobPrincipal = obtainTestToken(baseUrl, uid = "bob-$suffix")
  ensureUserExists(baseUrl, alicePrincipal.token, username = aliceName)
  ensureUserExists(baseUrl, bobPrincipal.token, username = bobName)

  IntegrationHarness(baseUrl, alicePrincipal.token).use { alice ->
    IntegrationHarness(baseUrl, bobPrincipal.token).use { bob ->
      alice.loadAndAwaitInit()
      bob.loadAndAwaitInit()
      alice.api.reloadFriends()
      bob.api.reloadFriends()

      val bobFriendAdded = bob.scope.async {
        bob.api.eventFlow.first { it is ChannelMessage.FriendAdded }
      }
      val aliceFriendAdded = alice.scope.async {
        alice.api.eventFlow.first { it is ChannelMessage.FriendAdded }
      }

      sendFriendRequestHttp(baseUrl, bob.token, "id:${alicePrincipal.scopedSystemId}")
      acceptFriendRequestHttp(baseUrl, alice.token, "id:${bobPrincipal.scopedSystemId}")

      withContext(app.interfold.app.utils.ioDispatcher) {
        withTimeout(20.seconds) {
          bobFriendAdded.await()
          aliceFriendAdded.await()
        }
      }

      registerPushToken(
        baseUrl,
        bobPrincipal.token,
        "it-desktop-placeholder-$suffix",
      )

      val created = alice.scope.async {
        alice.api.eventFlow.first { it is ChannelMessage.AlterCreated }
      }
      alice.api.createAlter(alterName)
      val alter = withContext(app.interfold.app.utils.ioDispatcher) {
        withTimeout(15.seconds) { created.await() as ChannelMessage.AlterCreated }
      }.alter
      setAlterFriendsOnly(baseUrl, alicePrincipal.token, alter.id)

      val fronted = alice.scope.async {
        alice.api.eventFlow.first { it is ChannelMessage.FrontingStarted }
      }
      alice.api.startFront(alter.id)
      withContext(app.interfold.app.utils.ioDispatcher) {
        withTimeout(15.seconds) { fronted.await() }
      }

      val send = awaitFcmSend(baseUrl, alicePrincipal.token, alicePrincipal.scopedSystemId)
      check(send.skipReason != FCM_SKIP_NULL) {
        "checkpoint FCM enabled: outbox recorded $FCM_SKIP_NULL (service account not seeded)"
      }
      check(send.skipReason != FCM_SKIP_NO_TOKENS) {
        "checkpoint tokens: no friend tokens for ${alicePrincipal.scopedSystemId}"
      }
      check(send.skipReason != FCM_SKIP_MESSAGING) {
        "checkpoint Firebase Messaging: Admin SDK did not initialise"
      }
      check(send.skipReason != FCM_SKIP_NO_VISIBLE) {
        "checkpoint visibility: friend ${bobPrincipal.scopedSystemId} could not see fronter"
      }
      check(send.skipReason == null) {
        "checkpoint Firebase send: skipped (${send.skipReason}) title=${send.title}"
      }
      check(
        send.friendSystemId.isNullOrBlank() ||
          send.friendSystemId == bobPrincipal.scopedSystemId ||
          send.friendSystemId.contains(bobPrincipal.scopedSystemId),
      ) {
        "checkpoint tokens: send targeted ${send.friendSystemId}, expected ${bobPrincipal.scopedSystemId}"
      }
      assertTrue(
        send.title == "Front update: $aliceName",
        "checkpoint title: expected 'Front update: $aliceName', got '${send.title}'",
      )
      assertTrue(
        send.body?.contains(alterName) == true,
        "checkpoint body: expected alter '$alterName' in '${send.body}'",
      )
      assertTrue(
        send.alterIds.isNotEmpty(),
        "checkpoint alter_ids: empty on send $send",
      )
      val acceptedOrKnownInvalid = send.successCount > 0 ||
        send.errorCodes.any {
          it.equals("Unregistered", ignoreCase = true) ||
            it.equals("InvalidArgument", ignoreCase = true)
        }
      assertTrue(
        acceptedOrKnownInvalid,
        "checkpoint Firebase send: success=${send.successCount} failure=${send.failureCount} " +
          "errors=${send.errorCodes}",
      )
    }
  }
}
