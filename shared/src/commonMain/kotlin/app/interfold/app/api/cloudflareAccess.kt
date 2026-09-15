package app.interfold.app.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Process-wide Cloudflare Access application JWT used for instance HTTP/WS.
 * Synced from [app.interfold.app.Settings.cloudflareAccessJwt]; never sent as `Authorization`.
 */
object CloudflareAccessCredentials {
  const val JWT_ASSERTION_HEADER = "Cf-Access-Jwt-Assertion"
  const val COOKIE_NAME = "CF_Authorization"

  /** Skew window before Access JWT `exp` when foreground silent rotation should run. */
  val nearExpirySkew = 5.minutes

  @Volatile
  var accessJwt: String? = null
    private set

  fun update(jwt: String?) {
    accessJwt = jwt?.takeIf { it.isNotBlank() }
  }
}

fun HttpClientConfig<*>.installCloudflareAccessHeaders() {
  defaultRequest {
    CloudflareAccessCredentials.accessJwt?.let { jwt ->
      header(CloudflareAccessCredentials.JWT_ASSERTION_HEADER, jwt)
    }
  }
}

@OptIn(ExperimentalEncodingApi::class)
fun parseAccessJwtExpiryEpochSeconds(jwt: String): Long? {
  val parts = jwt.split('.')
  if (parts.size < 2) return null
  return try {
    val padded = parts[1].let { segment ->
      val mod = segment.length % 4
      if (mod == 0) segment else segment + "=".repeat(4 - mod)
    }
    val json = Base64.UrlSafe.decode(padded).decodeToString()
    Json.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.longOrNull
  } catch (_: Exception) {
    null
  }
}

@OptIn(ExperimentalTime::class)
fun isAccessJwtNearExpiry(
  jwt: String?,
  nowEpochSeconds: Long = Clock.System.now().epochSeconds,
): Boolean {
  if (jwt.isNullOrBlank()) return false
  val exp = parseAccessJwtExpiryEpochSeconds(jwt) ?: return false
  return exp <= nowEpochSeconds + CloudflareAccessCredentials.nearExpirySkew.inWholeSeconds
}

/**
 * Heuristic: response body/location looks like a Cloudflare Access challenge rather than Interfold JSON.
 */
fun looksLikeCloudflareAccessChallenge(
  statusCode: Int,
  locationHeader: String?,
  bodySnippet: String?,
): Boolean {
  val location = locationHeader.orEmpty().lowercase()
  if (location.contains("cloudflareaccess.com") || location.contains("/cdn-cgi/access/")) {
    return true
  }
  val body = bodySnippet.orEmpty().lowercase()
  if (body.contains("cloudflareaccess.com") || body.contains("cf-access-domain")) {
    return true
  }
  // Access often serves HTML login pages as 200/302/403 without Interfold JSON shape.
  if (statusCode in listOf(302, 401, 403) && body.contains("<html")) {
    return true
  }
  return false
}
