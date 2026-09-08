package app.interfold.app.ui.model.main.supportus

import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.utils.ColorSchemeParams

interface SupportUsComponent {
  val settings: SettingsInterface

  fun openPatreon(colorSchemeParams: ColorSchemeParams)
  fun openKofi(colorSchemeParams: ColorSchemeParams)

  fun navigateBack()
}

const val PATREON_URL = "https://www.patreon.com/interfold"
const val KOFI_URL = "https://ko-fi.com/atlasoc"

class SupportUsComponentImpl(
  componentContext: MainComponentContext,
  private val popSelf: () -> Unit
) : SupportUsComponent, MainComponentContext by componentContext {
  override fun openPatreon(colorSchemeParams: ColorSchemeParams) {
    platformUtilities.openURL(PATREON_URL, colorSchemeParams)
  }

  override fun openKofi(colorSchemeParams: ColorSchemeParams) {
    platformUtilities.openURL(KOFI_URL, colorSchemeParams)
  }

  override fun navigateBack() = popSelf()
}