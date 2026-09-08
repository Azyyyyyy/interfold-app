package app.interfold.app.ui.model.main.settings

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface SettingsSecurityComponent : CommonInterface {
  fun navigateBack()
}

class SettingsSecurityComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsSecurityComponent, MainComponentContext by componentContext {
  override fun navigateBack() = popSelf()
}