package app.interfold.app.telemetry

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal const val BROWSER_ERROR_EVENT = "Browser error event"

private const val MAX_BROWSER_ERROR_MESSAGE = 400
private const val MAX_BROWSER_ERROR_STACK = 500
private const val MAX_BROWSER_ERROR_STACK_LINES = 6

private val jsObjectTagRegex = Regex("""\[object(?:\s+([A-Za-z]+))?\]""")
private val quotedEventTypeRegex = Regex(""""type"\s*:\s*"([^"]+)"""")
private val bareEventTypeRegex = Regex("""\btype\s*:\s*([A-Za-z0-9_-]+)""")
private val jsExceptionPrefixRegex =
  Regex("""^(JsException|Throwable|Exception|Error):\s*""")
private val uncaughtJsErrorTypeRegex =
  Regex("""^(?:Uncaught\s+)?([A-Za-z]+Error)\s*:""")
private val urlWithQueryRegex =
  Regex("""((?:https?|wss?)://[^\s?#]+)\?[^\s#]*""")
private val dataUrlRegex = Regex("""data:[^\s]*""")
private val genericBrowserKinds = setOf(
  "event",
  "object",
  "error",
  "errorevent",
  "progressevent",
  "unhandledrejection",
)

private val browserErrorJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
  coerceInputValues = true
}

/**
 * Facts extracted from a browser `error` / `unhandledrejection` event, or from
 * the value a JavaScript exception still holds. The wasm extractor fills this;
 * [reportBrowserError] turns it into an activity message.
 */
@Serializable
internal data class BrowserErrorFacts(
  val eventType: String = "",
  val message: String = "",
  val filename: String = "",
  val line: Int = 0,
  val column: Int = 0,
  val errorName: String = "",
  val errorMessage: String = "",
  val errorStack: String = "",
  val errorKind: String = "",
  val nestedType: String = "",
  val targetKind: String = "",
  val targetDetail: String = "",
  val describeFailure: String = "",
)

internal data class BrowserErrorReport(
  val message: String,
  val attributes: Map<String, String>,
)

/**
 * Browser WebSocket / IndexedDB failures often reject with a DOM Event rather
 * than an Error. Kotlin/Wasm then stringifies that as `[object Event]` or a
 * `{ type: error, isTrusted: true }` dump. Collapse those into a readable
 * label. When the original JS value is still available,
 * [platformExceptionAttributes] supplies the target, source, and stack first.
 */
internal fun readableExceptionMessage(
  throwable: Throwable,
  fallback: String = "Unknown error",
): String {
  platformExceptionAttributes(throwable)["exception.message"]
    ?.takeIf { it.isNotBlank() }
    ?.let { return it }
  return fallbackExceptionMessage(throwable, fallback)
}

internal fun readableExceptionType(throwable: Throwable): String {
  jsErrorTypeFromMessage(throwable.message)?.let { return it }
  jsErrorTypeFromMessage(throwable.cause?.message)?.let { return it }
  return throwable::class.simpleName?.takeIf { it.isNotBlank() } ?: "Throwable"
}

/**
 * Message plus the extra activity fields (type, source, target, stack) for a
 * throwable. Wasm fills the extra fields from the live JS error or event.
 */
@PublishedApi
internal fun exceptionActivityAttributes(throwable: Throwable): Map<String, String> {
  val platform = platformExceptionAttributes(throwable)
  val message = platform["exception.message"]?.takeIf { it.isNotBlank() }
    ?: fallbackExceptionMessage(throwable)
  return buildMap {
    put(
      "exception.type",
      platform["exception.type"]?.takeIf { it.isNotBlank() } ?: readableExceptionType(throwable),
    )
    put("exception.message", message)
    platform.forEach { (key, value) ->
      if (value.isBlank() || key == "exception.type" || key == "exception.message") return@forEach
      put(key, value)
    }
  }
}

internal expect fun platformExceptionAttributes(throwable: Throwable): Map<String, String>

internal fun reportBrowserError(facts: BrowserErrorFacts): BrowserErrorReport {
  val source = formatBrowserErrorSource(facts.filename, facts.line, facts.column)
  val target = formatBrowserErrorTarget(facts.targetKind, facts.targetDetail)
  val stack = clipBrowserErrorStack(stripSensitiveUrls(facts.errorStack))
  val name = facts.errorName.trim()
  val named = specificBrowserKind(name)
  val thrownMessage = meaningfulBrowserText(facts.errorMessage, name)
  val eventMessage = meaningfulBrowserText(facts.message, name = "")
  val failure = stripSensitiveUrls(facts.describeFailure.trim())

  val primary = when {
    thrownMessage.isNotEmpty() -> prefixErrorName(named, thrownMessage)
    named != null -> named
    eventMessage.isNotEmpty() -> eventMessage
    facts.message.trim() == "Script error." -> "Script error (cross-origin)"
    failure.isNotEmpty() -> "$BROWSER_ERROR_EVENT · $failure"
    else -> BROWSER_ERROR_EVENT
  }

  val hint = when {
    target.isNotEmpty() && !primary.contains(target) -> target
    primary == BROWSER_ERROR_EVENT -> browserErrorHint(facts)
    else -> ""
  }

  val message = clipBrowserErrorText(
    stripSensitiveUrls(
      buildString {
        append(primary)
        if (hint.isNotEmpty()) {
          append(" · ")
          append(hint)
        }
        if (source.isNotEmpty() && !contains(source)) {
          append(" @ ")
          append(source)
        }
      },
    ),
    MAX_BROWSER_ERROR_MESSAGE,
  )

  val type = named
    ?: jsErrorTypeFromMessage(message)
    ?: specificBrowserKind(facts.errorKind)
    ?: specificBrowserKind(facts.targetKind)
    ?: if (facts.eventType.equals("unhandledrejection", ignoreCase = true)) {
      "UnhandledRejection"
    } else {
      "ErrorEvent"
    }

  return BrowserErrorReport(
    message = message,
    attributes = buildMap {
      put("exception.type", type)
      if (source.isNotEmpty()) put("exception.source", source)
      if (target.isNotEmpty()) put("exception.target", target)
      if (stack.isNotEmpty()) put("exception.stacktrace", stack)
    },
  )
}

internal fun reportBrowserErrorJson(raw: String): BrowserErrorReport {
  val facts = try {
    browserErrorJson.decodeFromString(BrowserErrorFacts.serializer(), raw)
  } catch (_: Exception) {
    BrowserErrorFacts(
      describeFailure = raw.trim().take(200).ifBlank { "unreadable browser event" },
    )
  }
  return reportBrowserError(facts)
}

private fun fallbackExceptionMessage(
  throwable: Throwable,
  fallback: String = "Unknown error",
): String {
  normalizeOpaqueJsObjectMessage(throwable.message)?.takeIf { it.isNotBlank() }?.let { return it }
  throwable.cause?.let { cause ->
    normalizeOpaqueJsObjectMessage(cause.message)?.takeIf { it.isNotBlank() }?.let { return it }
  }
  return throwable::class.simpleName?.takeIf { it.isNotBlank() } ?: fallback
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

private fun browserErrorHint(facts: BrowserErrorFacts): String =
  specificBrowserKind(facts.errorKind)
    ?: specificBrowserKind(facts.nestedType)
    ?: specificBrowserKind(facts.eventType)
    ?: if (facts.eventType.equals("unhandledrejection", ignoreCase = true)) {
      "unhandledrejection"
    } else {
      ""
    }

private fun specificBrowserKind(raw: String): String? {
  val text = raw.trim()
  if (text.isEmpty() || text.startsWith("[object")) return null
  if (text.lowercase() in genericBrowserKinds) return null
  return text
}

private fun meaningfulBrowserText(raw: String, name: String): String {
  val text = raw.trim()
  if (text.isEmpty() || text == "Script error." || text.startsWith("[object ")) return ""
  val bare = text.trimEnd(':').trim()
  if (name.isNotEmpty() && bare.equals(name, ignoreCase = true)) return ""
  return text
}

private fun prefixErrorName(name: String?, message: String): String {
  if (name.isNullOrEmpty()) return message
  if (message.startsWith("$name:") || message.startsWith("Uncaught $name")) return message
  return "$name: $message"
}

private fun formatBrowserErrorSource(filename: String, line: Int, column: Int): String {
  val file = stripSensitiveUrls(filename.trim())
  if (file.isEmpty() && line <= 0) return ""
  return buildString {
    append(file)
    if (line > 0) {
      if (isNotEmpty()) append(':')
      append(line)
      if (column > 0) {
        append(':')
        append(column)
      }
    }
  }
}

private fun formatBrowserErrorTarget(kind: String, detail: String): String {
  val safeKind = specificBrowserKind(kind).orEmpty()
  val safeDetail = stripSensitiveUrls(detail.trim())
  return listOf(safeKind, safeDetail).filter { it.isNotEmpty() }.joinToString(" ")
}

private fun clipBrowserErrorStack(stack: String): String {
  val lines = stack.lineSequence()
    .map { it.trimEnd() }
    .filter { it.isNotBlank() }
    .take(MAX_BROWSER_ERROR_STACK_LINES)
    .toList()
  return clipBrowserErrorText(lines.joinToString("\n"), MAX_BROWSER_ERROR_STACK)
}

private fun clipBrowserErrorText(text: String, max: Int): String {
  val trimmed = text.trim()
  if (trimmed.length <= max) return trimmed
  if (max <= 3) return trimmed.take(max)
  return trimmed.take(max - 3).trimEnd() + "..."
}

private fun stripSensitiveUrls(text: String): String {
  if (text.isEmpty()) return text
  val withoutQuery = urlWithQueryRegex.replace(text) { it.groupValues[1] }
  return dataUrlRegex.replace(withoutQuery) { match ->
    match.value.substringBefore(';').substringBefore(',').take(48)
  }
}
