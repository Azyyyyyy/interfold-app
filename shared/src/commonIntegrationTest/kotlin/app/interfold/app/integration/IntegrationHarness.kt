package app.interfold.app.integration

import app.interfold.app.Settings
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.ui.model.interfaces.SettingsInterfaceImpl
import app.interfold.app.utils.ioDispatcher
import app.interfold.app.utils.platformUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Real [ApiInterfaceImpl] graph pointed at a running in-memory backend.
 * Visible to both JVM and wasm integration tests in this module (`internal`).
 */
internal class IntegrationHarness(val baseUrl: String, val token: String) : AutoCloseable {
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

  private val settings = SettingsInterfaceImpl(
    initialSettings = Settings(apiEndpoint = baseUrl, token = token),
    settingsSaver = { },
    platformUtilities = platformUtilities,
  )

  val api: ApiInterfaceImpl = ApiInterfaceImpl(
    coroutineScope = scope,
    platformUtilities = platformUtilities,
    settingsInterface = settings,
  )

  suspend fun loadAndAwaitInit(timeout: Duration = 30.seconds) {
    api.loadClient(token)
    withContext(ioDispatcher) {
      withTimeout(timeout) { api.initComplete.first { it } }
    }
  }

  override fun close() {
    api.onDestroy()
    scope.cancel()
  }
}
