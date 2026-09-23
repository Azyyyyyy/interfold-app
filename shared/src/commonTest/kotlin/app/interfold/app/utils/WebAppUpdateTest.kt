package app.interfold.app.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebAppUpdateTest {
  @Test
  fun announcesWhenIncomingVersionDiffers() {
    assertTrue(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
        dismissedVersion = null,
      )
    )
  }

  @Test
  fun staysQuietWhenVersionsMatch() {
    assertFalse(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-69",
        incomingVersion = "2026.09.20-69",
        dismissedVersion = null,
      )
    )
  }

  @Test
  fun staysQuietForMissingOrBlankIncomingVersion() {
    assertFalse(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-63",
        incomingVersion = null,
        dismissedVersion = null,
      )
    )
    assertFalse(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "",
        dismissedVersion = null,
      )
    )
  }

  @Test
  fun staysQuietWhenIncomingVersionWasDismissed() {
    assertFalse(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-69",
        dismissedVersion = "2026.09.20-69",
      )
    )
  }

  @Test
  fun announcesAgainForADifferentVersionAfterDismiss() {
    assertTrue(
      shouldAnnounceWebAppUpdate(
        runningBuildId = "2026.09.20-63",
        incomingVersion = "2026.09.20-70",
        dismissedVersion = "2026.09.20-69",
      )
    )
  }
}
