@file:OptIn(ExperimentalEncodingApi::class, ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalWasmJsInterop

actual suspend fun saveExportedBytes(suggestedFileName: String, bytes: ByteArray): Boolean {
  val base64 = Base64.encode(bytes)
  triggerBrowserDownload(suggestedFileName, base64)
  return true
}

@JsFun(
  "(fileName, base64) => {" +
    "const binary = atob(base64);" +
    "const len = binary.length;" +
    "const bytes = new Uint8Array(len);" +
    "for (let i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);" +
    "const blob = new Blob([bytes], { type: 'application/json' });" +
    "const url = URL.createObjectURL(blob);" +
    "const a = document.createElement('a');" +
    "a.href = url;" +
    "a.download = fileName;" +
    "document.body.appendChild(a);" +
    "a.click();" +
    "document.body.removeChild(a);" +
    "URL.revokeObjectURL(url);" +
    "}"
)
private external fun triggerBrowserDownload(fileName: String, base64: String)
