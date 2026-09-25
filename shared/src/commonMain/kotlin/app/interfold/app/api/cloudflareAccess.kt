package app.interfold.app.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Process-wide Cloudflare Access application JWT used for instance HTTP/WS.
 * Synced from [app.interfold.app.Settings.cloudflareAccessJwt]; never sent as `Authorization`.
 */
object CloudflareAccessCredentials {
  const val JWT_ASSERTION_HEADER = "Cf-Access-Jwt-Assertion"
  const val COOKIE_NAME = "CF_Authorization"

  const val DEFAULT_NEAR_EXPIRY_SKEW_MINUTES = 5

  /**
   * Skew window before Access JWT `exp` when foreground silent rotation should run.
   * Overridden from [app.interfold.app.Settings.cloudflareAccessNearExpirySkewMinutes].
   */
  @Volatile
  var nearExpirySkew: Duration = DEFAULT_NEAR_EXPIRY_SKEW_MINUTES.minutes
    private set

  @Volatile
  var accessJwt: String? = null
    private set

  /** Configured instance origin. Used so image fetches do not leak the JWT off-host. */
  @Volatile
  var apiEndpoint: String? = null
    private set

  fun update(
    jwt: String?,
    nearExpirySkewMinutes: Int = DEFAULT_NEAR_EXPIRY_SKEW_MINUTES,
    apiEndpoint: String? = this.apiEndpoint,
  ) {
    accessJwt = jwt?.takeIf { it.isNotBlank() }
    nearExpirySkew = nearExpirySkewMinutes.coerceAtLeast(1).minutes
    this.apiEndpoint = apiEndpoint?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
  }
}

/**
 * Attaches the stored Access application JWT for edge auth.
 *
 * Access authenticates non-browser clients on the `CF_Authorization` cookie
 * (browsers send it automatically). `Cf-Access-Jwt-Assertion` is kept as well
 * because that is the header Cloudflare forwards to the origin after a
 * successful Access check, and some setups accept the same JWT there.
 */
fun applyCloudflareAccessHeaders(headers: HeadersBuilder) {
  val jwt = CloudflareAccessCredentials.accessJwt ?: return
  if (headers[CloudflareAccessCredentials.JWT_ASSERTION_HEADER].isNullOrBlank()) {
    headers.append(CloudflareAccessCredentials.JWT_ASSERTION_HEADER, jwt)
  }
  val existingCookie = headers[HttpHeaders.Cookie]
  if (existingCookie.isNullOrBlank()) {
    headers.append(HttpHeaders.Cookie, "${CloudflareAccessCredentials.COOKIE_NAME}=$jwt")
  } else if (!existingCookie.contains(CloudflareAccessCredentials.COOKIE_NAME, ignoreCase = true)) {
    headers[HttpHeaders.Cookie] = "$existingCookie; ${CloudflareAccessCredentials.COOKIE_NAME}=$jwt"
  }
}

fun HttpRequestBuilder.applyCloudflareAccessHeaders() {
  applyCloudflareAccessHeaders(headers)
}

fun HttpClientConfig<*>.installCloudflareAccessHeaders() {
  // onRequest runs after the body is attached. defaultRequest headers are
  // dropped when Ktor rebuilds a multipart PUT (avatar upload).
  install(CloudflareAccessHeadersPlugin)
}

private val CloudflareAccessHeadersPlugin = createClientPlugin("CloudflareAccessHeaders") {
  onRequest { request, _ ->
    applyCloudflareAccessHeaders(request.headers)
  }
}

/**
 * Same headers as [installCloudflareAccessHeaders], but only when the request
 * targets the configured API origin. Kamel also loads third-party images
 * (stealth-mode articles); those must not receive the Access JWT.
 */
fun HttpClientConfig<*>.installCloudflareAccessHeadersForApiOrigin() {
  install(CloudflareAccessApiOriginPlugin)
}

private val CloudflareAccessApiOriginPlugin = createClientPlugin("CloudflareAccessApiOrigin") {
  onRequest { request, _ ->
    if (targetsConfiguredApiOrigin(request.url.build())) {
      applyCloudflareAccessHeaders(request.headers)
    }
  }
}

fun targetsConfiguredApiOrigin(
  requestUrl: Url,
  apiEndpoint: String? = CloudflareAccessCredentials.apiEndpoint,
): Boolean {
  val api = apiEndpoint
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { Url(it) }.getOrNull() }
    ?: return false
  return requestUrl.protocol.name.equals(api.protocol.name, ignoreCase = true) &&
    requestUrl.host.equals(api.host, ignoreCase = true) &&
    requestUrl.port == api.port
}

/**
 * Extracts the Cloudflare Access application JWT from a `Cookie` header or
 * `CookieManager.getCookie` blob (`name=value; name2=value2`).
 */
fun extractCfAuthorizationCookie(cookieBlob: String?): String? {
  if (cookieBlob.isNullOrBlank()) return null
  return cookieBlob
    .split(';')
    .asSequence()
    .map { it.trim() }
    .firstOrNull { it.startsWith("${CloudflareAccessCredentials.COOKIE_NAME}=", ignoreCase = true) }
    ?.substringAfter('=', missingDelimiterValue = "")
    ?.takeIf { it.isNotBlank() }
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
  skew: Duration = CloudflareAccessCredentials.nearExpirySkew,
): Boolean {
  if (jwt.isNullOrBlank()) return false
  val exp = parseAccessJwtExpiryEpochSeconds(jwt) ?: return false
  return exp <= nowEpochSeconds + skew.inWholeSeconds
}

/**
 * Seconds until Access JWT enters the near-expiry window, or `0` if already near/expired.
 * `null` when there is no usable JWT/`exp`.
 */
@OptIn(ExperimentalTime::class)
fun secondsUntilAccessJwtNearExpiry(
  jwt: String?,
  nowEpochSeconds: Long = Clock.System.now().epochSeconds,
  skew: Duration = CloudflareAccessCredentials.nearExpirySkew,
): Long? {
  if (jwt.isNullOrBlank()) return null
  val exp = parseAccessJwtExpiryEpochSeconds(jwt) ?: return null
  return (exp - skew.inWholeSeconds - nowEpochSeconds).coerceAtLeast(0)
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
