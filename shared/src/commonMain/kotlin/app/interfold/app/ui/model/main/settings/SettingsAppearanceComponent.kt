package app.interfold.app.ui.model.main.settings

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface SettingsAppearanceComponent : CommonInterface {
  fun navigateBack()
}

class SettingsAppearanceComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsAppearanceComponent, MainComponentContext by componentContext {
  override fun navigateBack() = popSelf()
}