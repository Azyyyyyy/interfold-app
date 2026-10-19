package app.interfold.app.utils

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write

actual suspend fun saveExportedBytes(suggestedFileName: String, bytes: ByteArray): Boolean {
  val baseName = suggestedFileName.substringBeforeLast('.')
  val extension = suggestedFileName.substringAfterLast('.', missingDelimiterValue = "json")
  val file = FileKit.openFileSaver(
    suggestedName = baseName,
    extension = extension,
  ) ?: return false
  file.write(bytes)
  return true
}
