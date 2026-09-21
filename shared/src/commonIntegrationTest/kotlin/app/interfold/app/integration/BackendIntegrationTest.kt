package app.interfold.app.integration

import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Live HTTP/WS tests against an in-memory Interfold backend. The scenarios live
 * here once; each platform supplies [runBackendIntegrationTest]:
 *  - desktop starts Testcontainers and [kotlinx.coroutines.runBlocking]
 *  - wasm uses a Gradle-injected URL/token (or no-ops on a plain browser unit run)
 */
class BackendIntegrationTest {

  @Test
  fun loadClient_completesInit_overInMemoryBackend() =
    runBackendIntegrationTest(timeout = 60.seconds, ::assertLoadClientCompletesOverBackend)

  @Test
  fun createAlter_emitsAlterCreatedEvent() =
    runBackendIntegrationTest(timeout = 60.seconds, ::assertCreateAlterEmitsEventOverBackend)
}
