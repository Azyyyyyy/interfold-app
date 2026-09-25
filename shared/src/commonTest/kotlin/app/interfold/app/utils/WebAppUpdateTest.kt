package app.interfold.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAppUpdateTest {
  private val running = "2026.09.20-63"
  private val incoming = "2026.09.20-69"
  private val later = "2026.09.20-70"
  private val runningCache = AppUpdatePolicy.appCacheName(running)
  private val incomingCache = AppUpdatePolicy.appCacheName(incoming)

  @Test
  fun pendingWhenIncomingVersionDiffers() {
    assertTrue(AppUpdatePolicy.isPending(running, incoming))
  }

  @Test
  fun notPendingWhenVersionsMatch() {
    assertFalse(AppUpdatePolicy.isPending(incoming, incoming))
  }

  @Test
  fun notPendingForMissingOrBlankIncomingVersion() {
    assertFalse(AppUpdatePolicy.isPending(running, null))
    assertFalse(AppUpdatePolicy.isPending(running, ""))
  }

  @Test
  fun noticeStaysQuietWhenIncomingVersionWasDismissed() {
    assertFalse(AppUpdatePolicy.shouldShowNotice(running, incoming, incoming))
  }

  @Test
  fun noticeShowsAgainForADifferentVersionAfterDismiss() {
    assertTrue(AppUpdatePolicy.shouldShowNotice(running, later, incoming))
  }

  @Test
  fun pendingRemainsAfterDismissSoSettingsCanOfferReload() {
    val session = AppUpdateSession(running)
    session.onIncomingVersion(incoming)
    assertTrue(session.snackbarVisible)
    assertTrue(session.settingsReloadVisible)

    session.dismissNotice()
    assertFalse(session.snackbarVisible)
    assertTrue(session.settingsReloadVisible)
    assertEquals(incoming, session.pendingVersion)
  }

  @Test
  fun firstInstallWithNoPreviousCacheAppliesNewBuild() {
    val decision = AppUpdatePolicy.servingCacheAfterActivate(
      currentCacheName = incomingCache,
      currentVersion = incoming,
      applyRequestedVersion = null,
      previousCacheNames = emptyList(),
      pinnedCacheName = null,
    )
    assertTrue(decision.applied)
    assertEquals(incomingCache, decision.servingCacheName)
  }

  @Test
  fun lastTabActivateWithoutApplyKeepsPinnedPreviousCache() {
    val session = AppUpdateSession(running)
    session.onIncomingVersion(incoming)
    session.dismissNotice()

    val decision = AppUpdatePolicy.servingCacheAfterActivate(
      currentCacheName = incomingCache,
      currentVersion = incoming,
      applyRequestedVersion = session.applyRequestedVersion,
      previousCacheNames = listOf(runningCache),
      pinnedCacheName = runningCache,
    )
    assertFalse(decision.applied)
    assertEquals(runningCache, decision.servingCacheName)
    assertFalse(
      AppUpdatePolicy.mayFetchAppBinaryFromNetwork(decision.servingCacheName, incomingCache)
    )
  }

  @Test
  fun lastTabActivateWithoutPinPicksCacheThatHasAppShell() {
    val emptyCache = AppUpdatePolicy.appCacheName("2026.09.20-01")
    val decision = AppUpdatePolicy.servingCacheAfterActivate(
      currentCacheName = incomingCache,
      currentVersion = incoming,
      applyRequestedVersion = null,
      previousCacheNames = listOf(emptyCache, runningCache),
      pinnedCacheName = null,
      hasAppShell = { it == runningCache },
    )
    assertFalse(decision.applied)
    assertEquals(runningCache, decision.servingCacheName)
  }

  @Test
  fun reloadThenActivateServesNewCacheAndDeletesOldAppCaches() {
    val session = AppUpdateSession(running)
    session.onIncomingVersion(incoming)
    session.dismissNotice()
    session.requestApply()

    val decision = AppUpdatePolicy.servingCacheAfterActivate(
      currentCacheName = incomingCache,
      currentVersion = incoming,
      applyRequestedVersion = session.applyRequestedVersion,
      previousCacheNames = listOf(runningCache),
      pinnedCacheName = runningCache,
    )
    assertTrue(decision.applied)
    assertEquals(incomingCache, decision.servingCacheName)
    assertTrue(
      AppUpdatePolicy.mayFetchAppBinaryFromNetwork(decision.servingCacheName, incomingCache)
    )

    val toDelete = AppUpdatePolicy.cachesToDeleteOnApply(
      allCacheNames = listOf(
        runningCache,
        incomingCache,
        AppUpdatePolicy.FIREBASE_CONFIG_CACHE,
        AppUpdatePolicy.SW_META_CACHE,
      ),
      currentCacheName = incomingCache,
    )
    assertEquals(listOf(runningCache), toDelete)
  }

  @Test
  fun laterIncomingVersionShowsNoticeAgainAfterPreviousDismiss() {
    val session = AppUpdateSession(running)
    session.onIncomingVersion(incoming)
    session.dismissNotice()
    session.onIncomingVersion(later)
    assertTrue(session.snackbarVisible)
    assertEquals(later, session.pendingVersion)
  }
}
