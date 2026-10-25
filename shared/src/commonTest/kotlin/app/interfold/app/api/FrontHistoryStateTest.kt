package app.interfold.app.api

import app.interfold.app.api.model.MyFront
import app.interfold.app.ui.compose.screens.main.hometabs.FrontHistoryItem
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrontHistoryStateTest {

  @Test
  fun removeFrontFromClusteredHistory_lastEntryForMonth_returnsNoDayGroups() {
    val clustered = clusterFrontData(listOf(sampleFront("front-only")), includeMonth = 3)

    assertTrue(clustered.isNotEmpty(), "precondition: the month must have had history before deletion")
    assertTrue(hasVisibleFrontHistory(clustered))

    val afterDelete = removeFrontFromClusteredHistory(clustered, "front-only")

    assertEquals(
      emptyList(),
      afterDelete,
      "empty day groups must be dropped so FrontHistoryScreen can show 'No front history for this month.'"
    )
    assertFalse(hasVisibleFrontHistory(afterDelete))
  }

  @Test
  fun removeFrontFromClusteredHistory_keepsOtherEntryOnSameDay() {
    val clustered = clusterFrontData(
      listOf(sampleFront("front-kept", startHour = 10), sampleFront("front-removed", startHour = 14)),
      includeMonth = 3
    )

    val afterDelete = removeFrontFromClusteredHistory(clustered, "front-removed")

    val remainingIds = afterDelete.flatMap { it.second }.map(FrontHistoryItem::frontID).distinct()
    assertEquals(listOf("front-kept"), remainingIds)
    assertTrue(hasVisibleFrontHistory(afterDelete))
  }

  @Test
  fun hasVisibleFrontHistory_isFalse_whenOnlyEmptyDayGroupsRemain() {
    val emptyGroups = listOf(
      Triple(15, Month.MARCH, 2024) to mutableListOf<FrontHistoryItem>()
    )

    assertFalse(hasVisibleFrontHistory(emptyGroups))
    assertTrue(emptyGroups.isNotEmpty())
  }

  private fun sampleFront(id: String, startHour: Int = 10): MyFront {
    val zone = TimeZone.currentSystemDefault()
    val start = LocalDateTime(2024, 3, 15, startHour, 0).toInstant(zone)
    val end = LocalDateTime(2024, 3, 15, startHour + 2, 0).toInstant(zone)
    return MyFront(
      id = id,
      alterID = 1,
      timeStart = start,
      timeEnd = end,
      userID = "system-fronts",
    )
  }
}
