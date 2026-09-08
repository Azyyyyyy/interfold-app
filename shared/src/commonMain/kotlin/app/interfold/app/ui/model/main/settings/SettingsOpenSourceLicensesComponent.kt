package app.interfold.app.ui.model.main.settings

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface SettingsOpenSourceLicensesComponent : CommonInterface {
  fun navigateBack()
}

class SettingsOpenSourceLicensesComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsOpenSourceLicensesComponent, MainComponentContext by componentContext {
  override fun navigateBack() = popSelf()
}