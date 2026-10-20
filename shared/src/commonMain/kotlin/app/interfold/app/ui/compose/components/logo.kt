package app.interfold.app.ui.compose.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.Group
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

object InterfoldBrandColors {
  val Primary = Color(0xFF035FFE)
  val SecondaryLight = Color(0xFF0B2250)
  val SecondaryDark = Color(0xFFF9F9F9)
}

sealed class LogoColorTokens(
  val primary: Color,
  val secondary: Color
) {
  companion object Default : LogoColorTokens(
    primary = InterfoldBrandColors.Primary,
    secondary = InterfoldBrandColors.SecondaryLight
  )

  object Dark : LogoColorTokens(
    primary = InterfoldBrandColors.Primary,
    secondary = InterfoldBrandColors.SecondaryDark
  )
}

private object InterfoldLogoPaths {
  val path1 = listOf(
    PathNode.MoveTo(514.257f, 726.721f),
    PathNode.CurveTo(474.409f, 734.331f, 477.472f, 675.096f, 423.746f, 654.873f),
    PathNode.CurveTo(394.619f, 642.485f, 343.536f, 634.433f, 332.501f, 607.572f),
    PathNode.CurveTo(326.812f, 593.326f, 337.586f, 575.674f, 350.186f, 568.127f),
    PathNode.CurveTo(381.556f, 548.847f, 429.029f, 537.593f, 464.208f, 550.232f),
    PathNode.LineTo(464.315f, 550.263f),
    PathNode.CurveTo(464.462f, 550.312f, 464.739f, 550.426f, 464.885f, 550.475f),
    PathNode.CurveTo(464.822f, 550.461f, 464.738f, 550.43f, 464.661f, 550.405f),
    PathNode.CurveTo(500.865f, 563.96f, 524.426f, 600.532f, 531.958f, 639.356f),
    PathNode.CurveTo(536.245f, 662.305f, 544.509f, 721.635f, 514.257f, 726.721f),
    PathNode.Close
  )
  val path2 = listOf(
    PathNode.MoveTo(781.7f, 511.486f),
    PathNode.LineTo(781.619f, 511.747f),
    PathNode.CurveTo(779.843f, 518.859f, 775.581f, 525.379f, 770.952f, 530.957f),
    PathNode.CurveTo(765.656f, 537.339f, 759.218f, 543.044f, 751.814f, 546.858f),
    PathNode.CurveTo(746.328f, 549.684f, 738.755f, 552.498f, 732.953f, 548.995f),
    PathNode.CurveTo(730.666f, 547.615f, 729.046f, 545.391f, 727.345f, 543.332f),
    PathNode.CurveTo(720.996f, 535.646f, 712.511f, 530.483f, 703.654f, 526.14f),
    PathNode.CurveTo(699.696f, 524.199f, 695.666f, 522.413f, 691.649f, 520.599f),
    PathNode.CurveTo(683.172f, 516.77f, 674.577f, 513.205f, 666.126f, 509.317f),
    PathNode.CurveTo(658.238f, 505.688f, 650.434f, 501.77f, 643.219f, 496.914f),
    PathNode.CurveTo(636.418f, 492.336f, 630.175f, 486.869f, 625.285f, 480.256f),
    PathNode.CurveTo(620.012f, 473.124f, 616.531f, 464.864f, 614.679f, 456.209f),
    PathNode.CurveTo(613.842f, 452.297f, 613.301f, 448.309f, 613.049f, 444.317f),
    PathNode.CurveTo(610.141f, 398.257f, 602.911f, 342.034f, 569.35f, 306.374f),
    PathNode.CurveTo(647.946f, 325.312f, 807.893f, 424.576f, 781.7f, 511.486f),
    PathNode.Close
  )
  val path5 = listOf(
    PathNode.MoveTo(653.439f, 611.353f),
    PathNode.CurveTo(640.611f, 634.482f, 612.902f, 670.471f, 581.901f, 665.712f),
    PathNode.CurveTo(581.312f, 665.613f, 580.727f, 665.499f, 580.145f, 665.369f),
    PathNode.CurveTo(568.163f, 662.701f, 559.222f, 652.64f, 557.204f, 640.531f),
    PathNode.CurveTo(546.881f, 578.573f, 501.328f, 516.787f, 427.577f, 520.548f),
    PathNode.CurveTo(394.085f, 522.032f, 354.14f, 532.599f, 329.084f, 553.623f),
    PathNode.CurveTo(319.879f, 561.346f, 309.273f, 567.325f, 297.632f, 570.301f),
    PathNode.CurveTo(291.953f, 571.753f, 275.682f, 575.31f, 261.481f, 566.241f),
    PathNode.CurveTo(254.295f, 561.652f, 250.689f, 555.745f, 248.088f, 551.484f),
    PathNode.CurveTo(237.287f, 533.793f, 239.216f, 514.913f, 240.313f, 508.066f),
    PathNode.CurveTo(243.141f, 490.419f, 252.275f, 478.88f, 257.127f, 472.903f),
    PathNode.CurveTo(257.127f, 472.903f, 271.831f, 453.807f, 298.47f, 444.183f),
    PathNode.CurveTo(300.244f, 443.542f, 302.702f, 442.607f, 304.468f, 442.015f),
    PathNode.CurveTo(304.55f, 441.983f, 304.631f, 441.95f, 304.713f, 441.934f),
    PathNode.CurveTo(306.024f, 441.464f, 307.379f, 441.065f, 308.706f, 440.63f),
    PathNode.CurveTo(327.979f, 434.759f, 348.219f, 432.257f, 368.183f, 432.089f),
    PathNode.CurveTo(374.741f, 432.017f, 381.34f, 432.241f, 387.791f, 432.692f),
    PathNode.CurveTo(442.423f, 435.558f, 483.191f, 466.042f, 523.419f, 500.433f),
    PathNode.CurveTo(523.435f, 500.466f, 523.452f, 500.482f, 523.484f, 500.498f),
    PathNode.CurveTo(526.027f, 502.666f, 528.586f, 504.834f, 531.161f, 507.034f),
    PathNode.CurveTo(531.307f, 507.164f, 531.471f, 507.295f, 531.617f, 507.425f),
    PathNode.CurveTo(549.966f, 522.378f, 568.013f, 537.827f, 586.595f, 552.526f),
    PathNode.CurveTo(586.628f, 552.542f, 586.66f, 552.559f, 586.693f, 552.591f),
    PathNode.CurveTo(598.692f, 562.074f, 610.922f, 571.27f, 623.644f, 579.746f),
    PathNode.CurveTo(630.103f, 584.209f, 636.788f, 588.289f, 643.823f, 591.775f),
    PathNode.CurveTo(645.178f, 592.487f, 646.408f, 593.063f, 647.816f, 593.894f),
    PathNode.CurveTo(648.109f, 594.057f, 648.386f, 594.236f, 648.663f, 594.399f),
    PathNode.CurveTo(649.005f, 594.627f, 649.364f, 594.855f, 649.723f, 595.084f),
    PathNode.CurveTo(651.125f, 596.029f, 652.461f, 597.105f, 653.504f, 598.262f),
    PathNode.CurveTo(653.537f, 598.278f, 653.553f, 598.311f, 653.569f, 598.344f),
    PathNode.CurveTo(654.547f, 599.42f, 655.231f, 600.577f, 655.492f, 601.783f),
    PathNode.CurveTo(656.328f, 604.855f, 654.987f, 608.432f, 653.439f, 611.353f),
    PathNode.Close
  )
  val path7 = listOf(
    PathNode.MoveTo(701.978f, 577.189f),
    PathNode.CurveTo(698.194f, 579.385f, 693.991f, 580.788f, 689.685f, 581.567f),
    PathNode.CurveTo(675.911f, 584.059f, 668.632f, 579.623f, 661.222f, 575.009f),
    PathNode.CurveTo(661.083f, 574.916f, 660.958f, 574.823f, 660.819f, 574.745f),
    PathNode.CurveTo(652.231f, 569.397f, 638.837f, 560.375f, 638.837f, 560.375f),
    PathNode.LineTo(638.837f, 560.359f),
    PathNode.CurveTo(606.484f, 539.71f, 578.022f, 513.217f, 547.281f, 488.646f),
    PathNode.CurveTo(503.332f, 451.115f, 457.849f, 412.329f, 389.375f, 408.298f),
    PathNode.CurveTo(375.098f, 407.213f, 359.983f, 407.399f, 345.256f, 408.794f),
    PathNode.CurveTo(341.039f, 409.197f, 336.869f, 409.709f, 332.746f, 410.298f),
    PathNode.CurveTo(330.7f, 410.608f, 328.654f, 410.918f, 326.623f, 411.275f),
    PathNode.CurveTo(320.562f, 412.314f, 314.655f, 413.585f, 309.012f, 415.058f),
    PathNode.CurveTo(304.47f, 416.252f, 300.114f, 417.585f, 295.975f, 419.058f),
    PathNode.CurveTo(293.386f, 419.973f, 290.41f, 419.647f, 288.394f, 417.802f),
    PathNode.CurveTo(287.765f, 417.227f, 287.265f, 416.508f, 287.265f, 416.508f),
    PathNode.CurveTo(287.265f, 416.508f, 286.343f, 415.158f, 286.037f, 413.384f),
    PathNode.CurveTo(282.502f, 392.883f, 328.125f, 358.692f, 328.126f, 358.692f),
    PathNode.CurveTo(371.443f, 326.229f, 420.901f, 293.882f, 488.683f, 296.792f),
    PathNode.CurveTo(570.659f, 300.311f, 584.192f, 376.83f, 588.672f, 446.125f),
    PathNode.CurveTo(589.013f, 450.59f, 589.556f, 454.822f, 590.3f, 458.821f),
    PathNode.CurveTo(591.027f, 462.805f, 591.959f, 466.588f, 593.075f, 470.153f),
    PathNode.CurveTo(594.734f, 475.501f, 596.78f, 480.384f, 599.183f, 484.88f),
    PathNode.CurveTo(600.78f, 487.872f, 602.532f, 490.693f, 604.423f, 493.36f),
    PathNode.CurveTo(611.027f, 502.677f, 619.336f, 510.087f, 628.684f, 516.412f),
    PathNode.CurveTo(635.365f, 520.923f, 642.589f, 524.892f, 650.108f, 528.628f),
    PathNode.CurveTo(666.649f, 536.829f, 684.663f, 543.851f, 701.637f, 552.951f),
    PathNode.LineTo(701.901f, 553.137f),
    PathNode.CurveTo(715.453f, 561.972f, 712.72f, 570.956f, 701.978f, 577.189f),
    PathNode.Close
  )
}

@Composable
fun interfoldLogoVectorPainter(
  logoColorTokens: LogoColorTokens = if (androidx.compose.foundation.isSystemInDarkTheme()) LogoColorTokens.Dark else LogoColorTokens.Default,
  @Suppress("UNUSED_PARAMETER") animate: Boolean = false
): VectorPainter {
  return rememberVectorPainter(
    defaultWidth = 800.dp,
    defaultHeight = 800.dp,
    viewportWidth = 1024f,
    viewportHeight = 1024f,
    autoMirror = false
  ) { _, _ ->
    Group(
      name = "logo",
      scaleX = 1.878456f,
      scaleY = 1.878456f,
      translationX = -449.77153f,
      translationY = -449.66752f
    ) {
      Path(
        name = "path1",
        pathData = InterfoldLogoPaths.path1,
        fill = SolidColor(logoColorTokens.secondary)
      )
      Path(
        name = "path2",
        pathData = InterfoldLogoPaths.path2,
        fill = SolidColor(logoColorTokens.primary)
      )
      Path(
        name = "path5",
        pathData = InterfoldLogoPaths.path5,
        fill = SolidColor(logoColorTokens.primary)
      )
      Path(
        name = "path7",
        pathData = InterfoldLogoPaths.path7,
        fill = SolidColor(logoColorTokens.secondary)
      )
    }
  }
}
