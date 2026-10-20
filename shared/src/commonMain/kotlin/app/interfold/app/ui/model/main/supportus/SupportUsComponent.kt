package app.interfold.app.ui.model.main.supportus

import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.utils.ColorSchemeParams

interface SupportUsComponent {
  val settings: SettingsInterface

  fun openGitHubSponsors(colorSchemeParams: ColorSchemeParams)

  fun navigateBack()
}

const val GITHUB_SPONSORS_URL = "https://github.com/sponsors/Azyyyyyy"

class SupportUsComponentImpl(
  componentContext: MainComponentContext,
  private val popSelf: () -> Unit
) : SupportUsComponent, MainComponentContext by componentContext {
  override fun openGitHubSponsors(colorSchemeParams: ColorSchemeParams) {
    platformUtilities.openURL(GITHUB_SPONSORS_URL, colorSchemeParams)
  }

  override fun navigateBack() = popSelf()
}
