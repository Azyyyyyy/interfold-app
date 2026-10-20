package app.interfold.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarImageTest {
  @Test
  fun avatarTargetSize_leavesSmallImagesUnchanged() {
    assertEquals(800 to 600, avatarTargetSize(800, 600))
  }

  @Test
  fun avatarTargetSize_scalesLongEdgeTo1024() {
    assertEquals(1024 to 768, avatarTargetSize(4096, 3072))
    assertEquals(768 to 1024, avatarTargetSize(3072, 4096))
  }
}
