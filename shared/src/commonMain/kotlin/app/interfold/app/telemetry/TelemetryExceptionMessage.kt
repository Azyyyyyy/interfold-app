package app.interfold.app.telemetry

internal const val BROWSER_ERROR_EVENT = "Browser error event"

private val jsObjectTagRegex = Regex("""\[object(?:\s+([A-Za-z]+))?\]""")
private val quotedEventTypeRegex = Regex(""""type"\s*:\s*"([^"]+)"""")
private val bareEventTypeRegex = Regex("""\btype\s*:\s*([A-Za-z0-9_-]+)""")
private val jsExceptionPrefixRegex =
  Regex("""^(JsException|Throwable|Exception|Error):\s*""")
private val uncaughtJsErrorTypeRegex =
  Regex("""^(?:Uncaught\s+)?([A-Za-z]+Error)\s*:""")

/**
 * Browser WebSocket / IndexedDB failures often reject with a DOM Event rather
 * than an Error. Kotlin/Wasm then stringifies that as `[object Event]` or a
 * `{ type: error, isTrusted: true }` dump, which is what the Activity page was
 * showing. Collapse those into a readable label and keep real messages intact.
 */
internal fun readableExceptionMessage(
  throwable: Throwable,
  fallback: String = "Unknown error",
): String {
  normalizeOpaqueJsObjectMessage(throwable.message)?.takeIf { it.isNotBlank() }?.let { return it }
  throwable.cause?.let { cause ->
    normalizeOpaqueJsObjectMessage(cause.message)?.takeIf { it.isNotBlank() }?.let { return it }
  }
  return throwable::class.simpleName?.takeIf { it.isNotBlank() } ?: fallback
}

internal fun readableExceptionType(throwable: Throwable): String {
  jsErrorTypeFromMessage(throwable.message)?.let { return it }
  jsErrorTypeFromMessage(throwable.cause?.message)?.let { return it }
  return throwable::class.simpleName?.takeIf { it.isNotBlank() } ?: "Throwable"
}

private fun jsErrorTypeFromMessage(message: String?): String? {
  val trimmed = message?.trim().orEmpty()
  if (trimmed.isEmpty()) return null
  return uncaughtJsErrorTypeRegex.find(trimmed)?.groupValues?.get(1)
}

internal fun normalizeOpaqueJsObjectMessage(text: String?): String? {
  if (text == null) return null
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return null
  if (looksLikeDomEventDump(trimmed)) {
    return browserEventMessage(extractEventType(trimmed))
  }
  if (!jsObjectTagRegex.containsMatchIn(trimmed)) return trimmed
  val replaced = jsObjectTagRegex.replace(trimmed) { match ->
    browserEventMessage(match.groupValues.getOrNull(1))
  }
  return jsExceptionPrefixRegex.replace(replaced, "").trim()
    .ifBlank { BROWSER_ERROR_EVENT }
}

private fun looksLikeDomEventDump(text: String): Boolean {
  if (!text.startsWith("{") || !text.endsWith("}")) return false
  val collapsed = text.replace(" ", "")
  val hasTrusted = collapsed.contains("isTrusted", ignoreCase = true)
  val hasType = collapsed.contains("\"type\":") || collapsed.contains("type:")
  val hasTarget = collapsed.contains("\"target\":") || collapsed.contains("target:")
  return hasTrusted && (hasType || hasTarget)
}

private fun extractEventType(text: String): String? =
  quotedEventTypeRegex.find(text)?.groupValues?.get(1)
    ?: bareEventTypeRegex.find(text)?.groupValues?.get(1)

private fun browserEventMessage(rawType: String?): String {
  val type = rawType?.trim().orEmpty()
  return when (type.lowercase()) {
    "", "event", "object", "error", "errorevent", "progressevent" -> BROWSER_ERROR_EVENT
    else -> "Browser $type event"
  }
}
