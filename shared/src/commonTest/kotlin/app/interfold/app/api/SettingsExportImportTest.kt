package app.interfold.app.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFails

class SettingsExportImportTest {
  @Test
  fun exportFileNameForFormat_usesInterfoldPrefix() {
    assertEquals("interfold_export_octocon.json", exportFileNameForFormat("octocon"))
    assertEquals("interfold_export_pk.json", exportFileNameForFormat("PK"))
    assertEquals("interfold_export_custom.json", exportFileNameForFormat("custom"))
  }

  @Test
  fun parseFormatsListBody_lowercasesEntries() {
    val formats = parseFormatsListBody("""{"data":["SP","pk","Octocon"]}""")
    assertEquals(listOf("sp", "pk", "octocon"), formats)
  }

  @Test
  fun parseImportOperationBody_queued() {
    val op = parseImportOperationBody(
      """{"data":{"operation_id":"11111111-1111-1111-1111-111111111111","status":"queued","started_at":"2026-01-02T03:04:05"}}"""
    )
    assertTrue(op.isQueued)
    assertFalse(op.isRunning)
    assertEquals("11111111-1111-1111-1111-111111111111", op.operationId)
  }

  @Test
  fun parseImportOperationBody_running() {
    val op = parseImportOperationBody(
      """{"data":{"operation_id":"22222222-2222-2222-2222-222222222222","status":"running","started_at":"2026-01-02T03:04:05"}}"""
    )
    assertTrue(op.isRunning)
    assertFalse(op.isQueued)
  }

  @Test
  fun parseImportOperationBody_missingData_fails() {
    assertFails {
      parseImportOperationBody("""{"error":"invalid_import_format"}""")
    }
  }

  @Test
  fun settingsLoginProviderVisibility_cloudflareHidesOauth() {
    val visibility = settingsLoginProviderVisibility(
      LoginMethods(cloudflare = true, google = true, discord = true, apple = true)
    )
    assertFalse(visibility.showGoogle)
    assertFalse(visibility.showDiscord)
    assertFalse(visibility.showApple)
  }

  @Test
  fun settingsLoginProviderVisibility_respectsFlags() {
    val visibility = settingsLoginProviderVisibility(
      LoginMethods(cloudflare = false, google = true, discord = false, apple = true)
    )
    assertTrue(visibility.showGoogle)
    assertFalse(visibility.showDiscord)
    assertTrue(visibility.showApple)
  }

  @Test
  fun channelAssociations_includeOctoconImportEvents() {
    assertTrue(ChannelMessage.associations.containsKey("octocon_import_complete"))
    assertTrue(ChannelMessage.associations.containsKey("octocon_import_failed"))
  }
}
