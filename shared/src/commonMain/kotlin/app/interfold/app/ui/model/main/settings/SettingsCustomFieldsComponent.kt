package app.interfold.app.ui.model.main.settings

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface SettingsCustomFieldsComponent : CommonInterface {
  fun navigateBack()
}

class SettingsCustomFieldsComponentImpl(
  componentContext: MainComponentContext,
  val popSelf: () -> Unit
) : SettingsCustomFieldsComponent, MainComponentContext by componentContext {
  override fun navigateBack() = popSelf()
}