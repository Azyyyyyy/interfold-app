package app.interfold.app.ui.model.main.settings

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface SettingsAccessibilityComponent : CommonInterface {
  fun navigateBack()
}

class SettingsAccessibilityComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsAccessibilityComponent, MainComponentContext by componentContext {
  override fun navigateBack() = popSelf()
}