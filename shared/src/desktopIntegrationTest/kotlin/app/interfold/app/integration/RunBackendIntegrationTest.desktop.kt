package app.interfold.app.integration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal fun runBackendIntegrationTest(
  timeout: Duration,
  block: suspend (baseUrl: String) -> Unit,
) {
  val container = sharedBackend
  runBlocking {
    withTimeout(timeout) { block(container.baseUrl) }
  }
}

private val sharedBackend: BackendContainer by lazy {
  BackendContainer().also { it.start() }
}
