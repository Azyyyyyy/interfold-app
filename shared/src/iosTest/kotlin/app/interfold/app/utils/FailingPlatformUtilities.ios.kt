package app.interfold.app.utils

actual class FailingPlatformUtilities actual constructor() :
  FailingPlatformUtilitiesBase(), PlatformUtilities {
  override var injectedPlatformDelegate: PlatformDelegate? = null
}
