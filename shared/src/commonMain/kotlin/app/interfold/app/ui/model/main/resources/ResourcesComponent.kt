package app.interfold.app.ui.model.main.resources

import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.utils.ColorSchemeParams

interface ResourcesComponent {
  val settings: SettingsInterface

  fun openResource(url: String, colorSchemeParams: ColorSchemeParams)
}

class ResourcesComponentImpl(
  componentContext: MainComponentContext
) : ResourcesComponent, MainComponentContext by componentContext {
  override fun openResource(url: String, colorSchemeParams: ColorSchemeParams) {
    platformUtilities.openURL(url, colorSchemeParams)
  }
}