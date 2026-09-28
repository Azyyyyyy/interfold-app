package app.interfold.app.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider

actual fun failingPlatformUtilities(): PlatformUtilities =
  object : FailingPlatformUtilitiesBase(), PlatformUtilities {
    override val context: Context = ApplicationProvider.getApplicationContext()
  }
