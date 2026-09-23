package app.interfold.app.telemetry

import app.interfold.app.api.CloudflareAccessCredentials

internal const val TELEMETRY_REDACTED = "[redacted]"

private val jwtLikeRegex =
  Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*\b""")

private val bearerTokenRegex =
  Regex("""\bBearer\s+\S+""", RegexOption.IGNORE_CASE)

private val cfAuthorizationCookieRegex =
  Regex(
    """\b${Regex.escape(CloudflareAccessCredentials.COOKIE_NAME)}\s*=\s*[^;\s]+""",
    RegexOption.IGNORE_CASE,
  )

/**
 * Scrubs Access JWTs, Bearer tokens, and auth cookie values from telemetry
 * attributes / messages before they hit the activity store or OTLP export.
 */
internal fun sanitizeTelemetryAttributes(
  attributes: Map<String, String>,
): Map<String, String> {
  if (attributes.isEmpty()) return attributes
  var changed = false
  val out = LinkedHashMap<String, String>(attributes.size)
  for ((key, value) in attributes) {
    val next = sanitizeTelemetryAttribute(key, value)
    out[key] = next
    if (next != value) changed = true
  }
  return if (changed) out else attributes
}

internal fun sanitizeTelemetryAttribute(key: String, value: String): String {
  if (value.isEmpty()) return value
  if (isSensitiveTelemetryAttributeKey(key)) return TELEMETRY_REDACTED
  return sanitizeTelemetryText(value) ?: value
}

internal fun sanitizeTelemetryText(text: String?): String? {
  if (text == null) return null
  if (text.isEmpty()) return text
  var result = text
  result = jwtLikeRegex.replace(result, TELEMETRY_REDACTED)
  result = bearerTokenRegex.replace(result, "Bearer $TELEMETRY_REDACTED")
  result = cfAuthorizationCookieRegex.replace(
    result,
    "${CloudflareAccessCredentials.COOKIE_NAME}=$TELEMETRY_REDACTED",
  )
  return normalizeOpaqueJsObjectMessage(result) ?: result
}

internal fun isSensitiveTelemetryAttributeKey(key: String): Boolean {
  val normalized = key.lowercase().replace('_', '-')
  return normalized.contains("authorization") ||
    normalized.contains("cf-access-jwt-assertion") ||
    normalized.contains("cf-authorization") ||
    normalized.contains("header.cookie") ||
    normalized.endsWith(".cookie") ||
    normalized.contains("set-cookie")
}
