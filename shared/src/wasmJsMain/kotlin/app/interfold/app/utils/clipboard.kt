@file:OptIn(ExperimentalWasmJsInterop::class)

package app.interfold.app.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.text.AnnotatedString
import kotlin.js.ExperimentalWasmJsInterop

/**
 * `NativeClipboard()` maps to `new Clipboard()`, which browsers reject
 * (`Illegal constructor`). Use the navigator clipboard instead.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun NativeClipboard.setText(annotatedString: AnnotatedString) {
  writeTextToNavigatorClipboard(annotatedString.text)
}

@JsFun(
  "(text) => {" +
    "try {" +
      "const c = navigator.clipboard;" +
      "if (!c || typeof c.writeText !== 'function') return;" +
      "const result = c.writeText(text);" +
      "if (result && typeof result.catch === 'function') result.catch(function () {});" +
    "} catch (e) {}" +
  "}"
)
private external fun writeTextToNavigatorClipboard(text: String)
