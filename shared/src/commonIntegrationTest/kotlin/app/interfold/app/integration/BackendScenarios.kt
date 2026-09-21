package app.interfold.app.integration

import app.interfold.app.api.APIState
import app.interfold.app.api.ChannelMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

internal suspend fun assertLoadClientCompletesOverBackend(baseUrl: String) {
  val principal = obtainTestToken(baseUrl)
  ensureUserExists(baseUrl, principal.token)

  IntegrationHarness(baseUrl, principal.token).use { h ->
    h.loadAndAwaitInit()

    assertTrue(h.api.initComplete.value, "initComplete should be true after loadClient")
    assertTrue(
      h.api.systemMe.value is APIState.Success,
      "systemMe should be Success, was ${h.api.systemMe.value}",
    )
    assertTrue(
      h.api.alters.value is APIState.Success,
      "alters should be Success, was ${h.api.alters.value}",
    )
    assertEquals(
      emptyList(),
      (h.api.alters.value as APIState.Success).data,
      "alters should be empty for a fresh principal",
    )
    assertTrue(
      h.api.tags.value is APIState.Success,
      "tags should be Success, was ${h.api.tags.value}",
    )
    assertTrue(
      h.api.fronts.value is APIState.Success,
      "fronts should be Success, was ${h.api.fronts.value}",
    )
  }
}

internal suspend fun assertCreateAlterEmitsEventOverBackend(baseUrl: String) {
  val principal = obtainTestToken(baseUrl)
  ensureUserExists(baseUrl, principal.token)

  IntegrationHarness(baseUrl, principal.token).use { h ->
    h.loadAndAwaitInit()

    val name = "Test-${Clock.System.now().toEpochMilliseconds()}"
    val firstAlterCreated = h.scope.async {
      h.api.eventFlow.first { it is ChannelMessage.AlterCreated }
    }

    h.api.createAlter(name)

    val createdEvent = withContext(app.interfold.app.utils.ioDispatcher) {
      withTimeout(15.seconds) {
        firstAlterCreated.await() as ChannelMessage.AlterCreated
      }
    }

    assertTrue(
      createdEvent.alter.name == name,
      "Expected AlterCreated event for '$name', got '${createdEvent.alter.name}'",
    )

    val alters = (h.api.alters.value as APIState.Success).data
    assertTrue(
      alters.any { it.name == name },
      "Expected alter '$name' to appear in alters StateFlow, got ${alters.map { it.name }}",
    )
  }
}
