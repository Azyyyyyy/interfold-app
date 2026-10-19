package app.interfold.app.utils

/**
 * Prompts the user to save [bytes] under [suggestedFileName] (e.g. `interfold_export_octocon.json`).
 * @return true if the file was written, false if the user cancelled.
 */
expect suspend fun saveExportedBytes(suggestedFileName: String, bytes: ByteArray): Boolean
