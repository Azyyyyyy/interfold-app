package app.interfold.app.telemetry

import app.interfold.app.api.CloudflareAccessCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryRedactionTest {
  private val sampleJwt =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0In0.signature"

  @Test
  fun redactsAccessJwtHeaderAttributesByKey() {
    val attrs = sanitizeTelemetryAttributes(
      mapOf(
        "http.request.header.cf-access-jwt-assertion" to sampleJwt,
        "http.method" to "GET",
      ),
    )
    assertEquals(TELEMETRY_REDACTED, attrs["http.request.header.cf-access-jwt-assertion"])
    assertEquals("GET", attrs["http.method"])
  }

  @Test
  fun redactsAuthorizationAndCookieHeaderAttributes() {
    val attrs = sanitizeTelemetryAttributes(
      mapOf(
        "http.request.header.authorization" to "Bearer interfold-token",
        "http.request.header.cookie" to "${CloudflareAccessCredentials.COOKIE_NAME}=$sampleJwt",
        "http.route" to "/api/me",
      ),
    )
    assertEquals(TELEMETRY_REDACTED, attrs["http.request.header.authorization"])
    assertEquals(TELEMETRY_REDACTED, attrs["http.request.header.cookie"])
    assertEquals("/api/me", attrs["http.route"])
  }

  @Test
  fun redactsJwtShapesEmbeddedInMessages() {
    val message = sanitizeTelemetryText(
      "Access failed with $sampleJwt and Bearer $sampleJwt; " +
        "${CloudflareAccessCredentials.COOKIE_NAME}=$sampleJwt; path=/",
    )!!
    assertFalse(message.contains(sampleJwt))
    assertTrue(message.contains(TELEMETRY_REDACTED))
    assertTrue(message.contains("Bearer $TELEMETRY_REDACTED"))
    assertTrue(message.contains("${CloudflareAccessCredentials.COOKIE_NAME}=$TELEMETRY_REDACTED"))
    assertTrue(message.contains("path=/"))
  }

  @Test
  fun activityStoreDoesNotRetainRawAccessJwt() {
    ClientActivityStore.clear()
    ClientActivityStore.record(
      name = "GET",
      scope = ActivityScope.APP,
      status = ActivityStatus.OK,
      durationMillis = 1,
      attributes = mapOf(
        "http.request.header.cf-access-jwt-assertion" to sampleJwt,
        "url.full" to "https://api.example/health",
      ),
      message = "cookie ${CloudflareAccessCredentials.COOKIE_NAME}=$sampleJwt",
    )

    val event = ClientActivityStore.snapshot().single()
    assertEquals(TELEMETRY_REDACTED, event.attributes["http.request.header.cf-access-jwt-assertion"])
    assertEquals("https://api.example/health", event.attributes["url.full"])
    assertEquals(
      "cookie ${CloudflareAccessCredentials.COOKIE_NAME}=$TELEMETRY_REDACTED",
      event.message,
    )
    assertTrue(event.attributes.values.none { it.contains("eyJ") })
    ClientActivityStore.clear()
  }
}
