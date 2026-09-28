package app.interfold.app.telemetry

import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

internal actual fun applyOtelExport(urls: List<String>) = Unit

internal actual fun emitOtelSpan(
  tracerName: String,
  name: String,
  status: ActivityStatus,
  durationMillis: Long,
  attributes: Map<String, String>,
) = Unit

internal actual fun startLiveEndpointSpan(
  name: String,
  attributes: Map<String, String>,
): Pair<Any?, Map<String, String>> = null to emptyMap()

internal actual fun endLiveEndpointSpan(
  token: Any?,
  status: ActivityStatus,
  attributes: Map<String, String>,
) = Unit

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun installUncaughtExceptionTelemetry() {
  window.addEventListener("error") { event ->
    runCatching { Telemetry.recordException(RuntimeException(jsErrorEventMessage(event))) }
  }
  window.addEventListener("unhandledrejection") { event ->
    runCatching { Telemetry.recordException(RuntimeException(jsErrorEventMessage(event))) }
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsErrorEventMessage(event: Event): String = describeJsErrorEvent(event)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
  "(event) => {" +
    "try {" +
      "const e = event;" +
      "if (!e) return 'Unknown error';" +
      "const message = e.message;" +
      "if (typeof message === 'string' && message && message !== 'Script error.' && message.indexOf('[object ') !== 0) return message;" +
      "const err = e.error || e.reason;" +
      "if (typeof err === 'string' && err && err.indexOf('[object ') !== 0) return err;" +
      "if (err && typeof err === 'object') {" +
        "const name = typeof err.name === 'string' ? err.name : '';" +
        "const errMessage = typeof err.message === 'string' ? err.message : '';" +
        "if (errMessage && errMessage.indexOf('[object ') !== 0) {" +
          "if (name && errMessage.indexOf(name + ':') !== 0 && errMessage.indexOf('Uncaught ' + name) !== 0) {" +
            "return name + ': ' + errMessage;" +
          "}" +
          "return errMessage;" +
        "}" +
        "if (name) return name;" +
        "if (typeof err.type === 'string' && err.type) return 'Browser ' + err.type + ' event';" +
      "}" +
      "if (typeof e.type === 'string' && e.type && e.type !== 'error' && e.type !== 'unhandledrejection') return 'Browser ' + e.type + ' event';" +
      "return 'Browser error event';" +
    "} catch (ex) { return 'Browser error event'; }" +
  "}"
)
private external fun describeJsErrorEvent(event: JsAny): String
