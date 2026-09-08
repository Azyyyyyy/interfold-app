package app.interfold.app.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.lexiereadable_bold
import interfoldapp.shared.resources.lexiereadable_regular
import interfoldapp.shared.resources.opendyslexic_bold
import interfoldapp.shared.resources.opendyslexic_regular
import interfoldapp.shared.resources.ubuntu_medium
import interfoldapp.shared.resources.ubuntu_regular
import org.jetbrains.compose.resources.Font


object Fonts {
  val ubuntu
    @Composable
    get() = FontFamily(
      Font(
        Res.font.ubuntu_regular,
        FontWeight.Normal,
        FontStyle.Normal
      ),

      Font(
        Res.font.ubuntu_medium,
        FontWeight.Medium,
        FontStyle.Normal
      )
    )

  val lexieReadable
    @Composable
    get() = FontFamily(
      Font(
        Res.font.lexiereadable_regular,
        FontWeight.Normal,
        FontStyle.Normal
      ),

      Font(
        Res.font.lexiereadable_bold,
        FontWeight.Medium,
        FontStyle.Normal
      )
    )

  val openDyslexic
    @Composable
    get() = FontFamily(
      Font(
        Res.font.opendyslexic_regular,
        FontWeight.Normal,
        FontStyle.Normal
      ),

      Font(
        Res.font.opendyslexic_bold,
        FontWeight.Medium,
        FontStyle.Normal
      )
    )
}