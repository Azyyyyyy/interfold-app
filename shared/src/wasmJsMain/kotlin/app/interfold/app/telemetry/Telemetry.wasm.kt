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

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(DESCRIBE_JS_ERROR_EVENT)
internal external fun describeJsErrorEvent(event: JsAny?): String

private const val DESCRIBE_JS_ERROR_EVENT =
  "(event) => {" +
    "const textOf = (value) => {" +
      "try {" +
        "if (value == null) return '';" +
        "const type = typeof value;" +
        "if (type === 'string') return value;" +
        "if (type === 'number' || type === 'boolean') return String(value);" +
        "if (type === 'object' && typeof value.toString === 'function') {" +
          "const viaToString = value.toString();" +
          "if (typeof viaToString === 'string' && viaToString && viaToString.indexOf('[object ') !== 0) return viaToString;" +
          "const viaString = String(value);" +
          "if (typeof viaString === 'string' && viaString && viaString.indexOf('[object ') !== 0) return viaString;" +
        "}" +
      "} catch (ex) {}" +
      "return '';" +
    "};" +
    "const useful = (value) => {" +
      "const text = textOf(value).trim();" +
      "if (!text || text === 'Script error.' || text.indexOf('[object ') === 0) return '';" +
      "return text;" +
    "};" +
    "const clip = (value, max) => {" +
      "const text = typeof value === 'string' ? value : textOf(value);" +
      "if (!text) return '';" +
      "return text.length > max ? text.slice(0, max) + '...' : text;" +
    "};" +
    "const clipUrl = (value) => {" +
      "const text = textOf(value).trim();" +
      "if (!text) return '';" +
      "if (text.indexOf('data:') === 0) {" +
        "const semi = text.indexOf(';');" +
        "const comma = text.indexOf(',');" +
        "const end = semi > 0 ? semi : (comma > 0 ? comma : Math.min(text.length, 40));" +
        "return text.slice(0, Math.min(end, 40));" +
      "}" +
      "return clip(text.split('#')[0].split('?')[0], 180);" +
    "};" +
    "const ctorName = (value) => {" +
      "try {" +
        "if (!value || (typeof value !== 'object' && typeof value !== 'function')) return '';" +
        "const name = value.constructor && value.constructor.name;" +
        "if (typeof name === 'string' && name && name !== 'Object') return name;" +
        "const tag = Object.prototype.toString.call(value);" +
        "if (tag.indexOf('[object ') === 0 && tag.charAt(tag.length - 1) === ']') {" +
          "const kind = tag.slice(8, -1);" +
          "if (kind && kind !== 'Object') return kind;" +
        "}" +
      "} catch (ex) {}" +
      "return '';" +
    "};" +
    "const isEventLike = (value) => {" +
      "if (!value || typeof value !== 'object') return false;" +
      "if (typeof value.preventDefault === 'function') return true;" +
      "const type = textOf(value.type);" +
      "return !!type && (value.target != null || type === 'error' || type === 'unhandledrejection' || type === 'abort' || type === 'close' || type === 'timeout');" +
    "};" +
    "const targetOf = (value) => {" +
      "try {" +
        "if (!value || (typeof value !== 'object' && typeof value !== 'function')) return null;" +
        "if (typeof window !== 'undefined' && (value === window || value === window.document)) return null;" +
        "return value;" +
      "} catch (ex) { return null; }" +
    "};" +
    "const describeTarget = (target) => {" +
      "const kind = ctorName(target) || textOf(target.tagName);" +
      "const bits = [];" +
      "const src = clipUrl(target.currentSrc || target.src || target.href || target.url || '');" +
      "if (src) bits.push(src);" +
      "if (typeof target.readyState === 'number') bits.push('readyState=' + target.readyState);" +
      "if (typeof target.status === 'number' && target.status) bits.push('status=' + target.status);" +
      "let errorName = '';" +
      "let errorMessage = '';" +
      "try {" +
        "const dbError = target.error;" +
        "if (dbError && typeof dbError === 'object' && !isEventLike(dbError)) {" +
          "errorName = useful(dbError.name);" +
          "errorMessage = useful(dbError.message);" +
        "}" +
      "} catch (ex) {}" +
      "return { kind: kind, detail: bits.join(' '), errorName: errorName, errorMessage: errorMessage };" +
    "};" +
    "const payload = (fields) => JSON.stringify({" +
      "eventType: fields.eventType || ''," +
      "message: fields.message || ''," +
      "filename: fields.filename || ''," +
      "line: fields.line || 0," +
      "column: fields.column || 0," +
      "errorName: fields.errorName || ''," +
      "errorMessage: fields.errorMessage || ''," +
      "errorStack: fields.errorStack || ''," +
      "errorKind: fields.errorKind || ''," +
      "nestedType: fields.nestedType || ''," +
      "targetKind: fields.targetKind || ''," +
      "targetDetail: fields.targetDetail || ''," +
      "describeFailure: fields.describeFailure || ''" +
    "});" +
    "try {" +
      "if (typeof event === 'string' || typeof event === 'number' || typeof event === 'boolean') {" +
        "return payload({ errorMessage: clip(useful(event), 300) });" +
      "}" +
      "const e = (event && typeof event === 'object') ? event : {};" +
      "const nested = isEventLike(e.reason) ? e.reason : (isEventLike(e.error) ? e.error : null);" +
      "let thrown = null;" +
      "if (isEventLike(e)) {" +
        "const candidate = e.error != null ? e.error : e.reason;" +
        "if (typeof candidate === 'string' || typeof candidate === 'number' || typeof candidate === 'boolean') thrown = candidate;" +
        "else if (candidate && typeof candidate === 'object' && !isEventLike(candidate)) thrown = candidate;" +
      "} else if (event && typeof event === 'object') {" +
        "thrown = event;" +
      "}" +
      "let errorName = '';" +
      "let errorMessage = '';" +
      "let errorStack = '';" +
      "let errorKind = '';" +
      "if (typeof thrown === 'string' || typeof thrown === 'number' || typeof thrown === 'boolean') {" +
        "errorMessage = useful(thrown);" +
      "} else if (thrown && typeof thrown === 'object') {" +
        "errorKind = ctorName(thrown);" +
        "errorName = textOf(thrown.name).trim();" +
        "errorMessage = useful(thrown.message);" +
        "errorStack = textOf(thrown.stack);" +
        "if (!errorMessage) {" +
          "const asString = textOf(thrown);" +
          "const lowered = asString.toLowerCase();" +
          "if (asString && lowered !== errorName.toLowerCase() && lowered !== 'event' && lowered !== 'object' && lowered !== 'error' && lowered !== 'errorevent') {" +
            "errorMessage = asString;" +
          "}" +
        "}" +
      "}" +
      "const carrier = nested || e;" +
      "const rawTarget = targetOf(carrier.target);" +
      "let targetKind = '';" +
      "let targetDetail = '';" +
      "if (rawTarget) {" +
        "const described = describeTarget(rawTarget);" +
        "targetKind = described.kind;" +
        "targetDetail = described.detail;" +
        "if (!errorMessage && described.errorMessage) {" +
          "errorName = described.errorName || errorName;" +
          "errorMessage = described.errorMessage;" +
        "} else if (!errorName && described.errorName) {" +
          "errorName = described.errorName;" +
        "}" +
      "}" +
      "const nestedType = nested ? textOf(nested.type).trim() : '';" +
      "return payload({" +
        "eventType: textOf(e.type).trim()," +
        "message: textOf(e.message).trim()," +
        "filename: clipUrl(e.filename)," +
        "line: typeof e.lineno === 'number' ? e.lineno : 0," +
        "column: typeof e.colno === 'number' ? e.colno : 0," +
        "errorName: errorName," +
        "errorMessage: clip(errorMessage, 300)," +
        "errorStack: clip(errorStack, 800)," +
        "errorKind: errorKind," +
        "nestedType: nestedType," +
        "targetKind: targetKind," +
        "targetDetail: targetDetail" +
      "});" +
    "} catch (ex) {" +
      "const failureName = textOf(ex && ex.name);" +
      "const failureMessage = textOf(ex && ex.message);" +
      "const failure = clip((failureName ? failureName + ': ' : '') + failureMessage, 200) || 'describe failed';" +
      "return payload({ describeFailure: failure });" +
    "}" +
  "}"
