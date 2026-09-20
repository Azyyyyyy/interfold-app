package app.interfold.app.api

import app.interfold.app.utils.globalSerializer
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudflareAccessTest {
  @AfterTest
  fun tearDown() {
    CloudflareAccessCredentials.update(null)
  }

  @Test
  fun extractCfAuthorizationCookie_readsCookieBlob() {
    val blob = "other=1; CF_Authorization=eyJhbGciOiJSUzI1NiJ9.e30.sig; path=/"
    assertEquals("eyJhbGciOiJSUzI1NiJ9.e30.sig", extractCfAuthorizationCookie(blob))
  }

  @Test
  fun extractCfAuthorizationCookie_missingReturnsNull() {
    assertNull(extractCfAuthorizationCookie("session=abc"))
  }

  @Test
  fun parseAccessJwtExpiry_readsExpClaim() {
    // {"exp":1700000000} base64url
    val payload = "eyJleHAiOjE3MDAwMDAwMDB9"
    val jwt = "hdr.$payload.sig"
    assertEquals(1_700_000_000L, parseAccessJwtExpiryEpochSeconds(jwt))
  }

  @Test
  fun isAccessJwtNearExpiry_respectsSkew() {
    val exp = 1_700_000_000L
    val payload = "eyJleHAiOjE3MDAwMDAwMDB9"
    val jwt = "hdr.$payload.sig"
    assertTrue(isAccessJwtNearExpiry(jwt, nowEpochSeconds = exp - 60))
    assertFalse(isAccessJwtNearExpiry(jwt, nowEpochSeconds = exp - 600))
  }

  @Test
  fun secondsUntilAccessJwtNearExpiry_schedulesAheadOfExp() {
    CloudflareAccessCredentials.update(jwt = null, nearExpirySkewMinutes = 5)
    val exp = 1_700_000_000L
    val payload = "eyJleHAiOjE3MDAwMDAwMDB9"
    val jwt = "hdr.$payload.sig"
    // 10 minutes before exp → 5 minutes until near-expiry window
    assertEquals(
      300L,
      secondsUntilAccessJwtNearExpiry(jwt, nowEpochSeconds = exp - 600),
    )
    assertEquals(
      0L,
      secondsUntilAccessJwtNearExpiry(jwt, nowEpochSeconds = exp - 60),
    )
  }

  @Test
  fun nearExpirySkew_isConfigurable() {
    CloudflareAccessCredentials.update(jwt = null, nearExpirySkewMinutes = 15)
    assertEquals(15, CloudflareAccessCredentials.nearExpirySkew.inWholeMinutes.toInt())
  }

  @Test
  fun looksLikeCloudflareAccessChallenge_detectsLocationAndHtml() {
    assertTrue(
      looksLikeCloudflareAccessChallenge(
        statusCode = 302,
        locationHeader = "https://myteam.cloudflareaccess.com/cdn-cgi/access/login",
        bodySnippet = null,
      )
    )
    assertTrue(
      looksLikeCloudflareAccessChallenge(
        statusCode = 403,
        locationHeader = null,
        bodySnippet = "<html>cloudflareaccess.com login</html>",
      )
    )
    assertFalse(
      looksLikeCloudflareAccessChallenge(
        statusCode = 200,
        locationHeader = null,
        bodySnippet = """{"cloudflare":false,"google":true,"discord":true,"apple":false}""",
      )
    )
  }

  @Test
  fun httpBuilder_attachesAccessJwtButNeverAsBearer() {
    CloudflareAccessCredentials.update("access-token-value")
    val builder = HttpRequestBuilder()
    httpBuilder("interfold-token", null).invoke(builder)

    assertEquals("Bearer interfold-token", builder.headers[HttpHeaders.Authorization])
    assertEquals(
      "access-token-value",
      builder.headers[CloudflareAccessCredentials.JWT_ASSERTION_HEADER],
    )
  }

  @Test
  fun httpBuilder_omitsAccessHeaderWhenUnset() {
    CloudflareAccessCredentials.update(null)
    val builder = HttpRequestBuilder()
    httpBuilder(null, null).invoke(builder)
    assertNull(builder.headers[CloudflareAccessCredentials.JWT_ASSERTION_HEADER])
    assertNull(builder.headers[HttpHeaders.Authorization])
  }

  @Test
  fun loginMethods_serializerRoundTrip() {
    val json = """{"cloudflare":true,"google":false,"discord":false,"apple":false}"""
    val methods = globalSerializer.decodeFromString(LoginMethods.serializer(), json)
    assertTrue(methods.cloudflare)
    assertFalse(methods.google)
  }
}
