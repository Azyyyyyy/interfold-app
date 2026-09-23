package app.interfold.app.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAppUpdateTest {
  @Test
  fun pendingWhenIncomingVersionDiffers() {
    assertTrue(
      isPendingWebAppVersion(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
      )
    )
  }

  @Test
  fun notPendingWhenVersionsMatch() {
    assertFalse(
      isPendingWebAppVersion(
        runningBuildId = "2026.09.20-69",
        incomingVersion = "2026.09.20-69",
      )
    )
  }

  @Test
  fun notPendingForMissingOrBlankIncomingVersion() {
    assertFalse(
      isPendingWebAppVersion(
        runningBuildId = "2026.09.20-63",
        incomingVersion = null,
      )
    )
    assertFalse(
      isPendingWebAppVersion(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "",
      )
    )
  }

  @Test
  fun noticeStaysQuietWhenIncomingVersionWasDismissed() {
    assertFalse(
      shouldShowWebAppUpdateNotice(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
        dismissedVersion = "2026.09.20-69",
      )
    )
  }

  @Test
  fun noticeShowsAgainForADifferentVersionAfterDismiss() {
    assertTrue(
      shouldShowWebAppUpdateNotice(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-70",
        dismissedVersion = "2026.09.20-69",
      )
    )
  }

  @Test
  fun pendingRemainsAfterDismissSoSettingsCanOfferReload() {
    assertTrue(
      isPendingWebAppVersion(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
      )
    )
    assertFalse(
      shouldShowWebAppUpdateNotice(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
        dismissedVersion = "2026.09.20-69",
      )
    )
  }
}
