package app.interfold.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize

import com.mr0xf00.easycrop.core.images.ImageSrc
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CoroutineScope

/** Match iOS avatar encode: long edge 1024, lossy WebP. */
const val AVATAR_MAX_EDGE_PX = 1024
const val AVATAR_WEBP_QUALITY = 80
const val AVATAR_MAX_BYTES = 512 * 1024

val avatarMaxCropSize = IntSize(AVATAR_MAX_EDGE_PX, AVATAR_MAX_EDGE_PX)

fun avatarTargetSize(width: Int, height: Int, maxEdge: Int = AVATAR_MAX_EDGE_PX): Pair<Int, Int> {
  val longest = maxOf(width, height)
  if (longest <= maxEdge) return width to height
  val scale = maxEdge.toDouble() / longest.toDouble()
  return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}

expect suspend fun platformFileToImageSrc(file: PlatformFile, platformUtilities: PlatformUtilities): ImageSrc?
expect fun directlyCompressImage(file: PlatformFile, platformUtilities: PlatformUtilities): ByteArray?
expect fun cropImageNatively(
  file: PlatformFile,
  platformUtilities: PlatformUtilities,
  onCompressionStart: () -> Unit,
  onImageReady: (ByteArray) -> Unit,
  onCanceled: () -> Unit,
  coroutineScope: CoroutineScope
)

expect suspend fun ImageBitmap.compress(): ByteArray