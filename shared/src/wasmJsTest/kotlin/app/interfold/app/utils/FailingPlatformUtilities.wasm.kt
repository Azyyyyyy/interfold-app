package app.interfold.app.utils

actual fun failingPlatformUtilities(): PlatformUtilities =
  object : FailingPlatformUtilitiesBase(), PlatformUtilities {}
