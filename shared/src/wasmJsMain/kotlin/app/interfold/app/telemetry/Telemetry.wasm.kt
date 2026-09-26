package app.interfold.app.telemetry

import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsException

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
    recordBrowserEvent(event)
  }
  window.addEventListener("unhandledrejection") { event ->
    recordBrowserEvent(event)
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
internal actual fun platformExceptionAttributes(throwable: Throwable): Map<String, String> {
  val thrown = jsThrownValue(throwable) ?: return emptyMap()
  val report = runCatching { reportBrowserErrorJson(describeJsErrorEvent(thrown)) }.getOrNull()
    ?: return emptyMap()
  val hasContext = report.message != BROWSER_ERROR_EVENT ||
    report.attributes.any { (key, value) -> key != "exception.type" && value.isNotBlank() }
  if (!hasContext) return emptyMap()
  return report.attributes + ("exception.message" to report.message)
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun recordBrowserEvent(event: Event) {
  runCatching {
    val report = reportBrowserErrorJson(describeJsErrorEvent(event))
    Telemetry.recordException(
      RuntimeException(report.message),
      report.attributes + ("exception.message" to report.message),
    )
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsThrownValue(throwable: Throwable): JsAny? {
  var current: Throwable? = throwable
  while (current != null) {
    val thrown = (current as? JsException)?.thrownValue
    if (thrown != null) return thrown
    current = current.cause
  }
  return null
}
