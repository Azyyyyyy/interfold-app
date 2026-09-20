package app.interfold.app.ui.model.main.settings

import app.interfold.app.telemetry.ActivityEvent
import app.interfold.app.telemetry.ClientActivityStore
import app.interfold.app.telemetry.OtlpDiscoveryState
import app.interfold.app.telemetry.Telemetry
import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface SettingsActivityComponent : CommonInterface {
  val events: StateFlow<List<ActivityEvent>>
  val discovery: StateFlow<OtlpDiscoveryState>
  fun navigateBack()
  fun setShareActivityWithServer(enabled: Boolean)
  fun setOtlpEndpoint(endpoint: String)
  fun clearActivity()
  fun refreshDiscovery()
}

class SettingsActivityComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsActivityComponent, MainComponentContext by componentContext {
  override val events: StateFlow<List<ActivityEvent>> = ClientActivityStore.events
  override val discovery: StateFlow<OtlpDiscoveryState> = Telemetry.discovery

  override fun navigateBack() = popSelf()

  override fun setShareActivityWithServer(enabled: Boolean) {
    settings.setShareActivityWithServer(enabled)
  }

  override fun setOtlpEndpoint(endpoint: String) {
    settings.setOtlpEndpoint(endpoint)
  }

  override fun clearActivity() {
    ClientActivityStore.clear()
  }

  override fun refreshDiscovery() {
    val endpoint = settings.data.value.apiEndpoint
    CoroutineScope(coroutineContext).launch {
      Telemetry.refreshDiscovery(endpoint)
    }
  }
}
