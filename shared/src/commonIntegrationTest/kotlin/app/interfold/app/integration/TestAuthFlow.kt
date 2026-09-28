package app.interfold.app.integration

import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlin.time.Clock

/**
 * Drives the in-memory backend through a real auth-callback handshake to
 * obtain a properly-issued, properly-recorded JWT.
 *
 * The published image's `IAuthTokenRevocationRepository` uses allow-list
 * semantics: unknown `jti` ⇒ rejected. The only path that calls
 * `RecordTokenAsync` is `AuthController.IssueDeepLinkTokenAsync`, invoked
 * exclusively from the OAuth callback handler. We therefore mint tokens
 * the same way the production client does: by going through
 * `GET /auth/discord/callback`.
 *
 * The harness uses the `?uid=<value>` fallback (instead of a real OAuth
 * `code` exchange) — see `OAuthControllerBase.ExtractProviderIdentityAsync`.
 *
 * Kotlin/Wasm browser tests cannot send the redirect cookie (browsers strip
 * the Cookie header on fetch), so [preMintedTestPrincipal] is filled from a
 * token the JVM backend process minted with OkHttp.
 */
internal suspend fun obtainTestToken(
  baseUrl: String,
  provider: String = "discord",
  uid: String = "test-${Clock.System.now().toEpochMilliseconds().toString(36)}",
  redirectUri: String = REDIRECT_PLACEHOLDER,
): TestPrincipal {
  preMintedTestPrincipal?.let { return it }

  val client = createNoRedirectHttpClient()
  try {
    val callback = "$baseUrl/auth/${provider}/callback?uid=$uid"
    val response = client.get(callback) {
      cookie(name = REDIRECT_COOKIE_NAME, value = redirectUri)
    }
    check(response.status.value in 300..399) {
      "Expected 30x from /auth/$provider/callback?uid=…, got ${response.status}: ${response.bodyAsText()}"
    }
    val location = response.headers[HttpHeaders.Location]
      ?: error("Callback response missing Location header")
    val parsed = URLBuilder(location).build()
    val token = parsed.parameters["token"]
      ?: error("Callback redirect URL has no 'token' query param: $location")
    val systemId = parsed.parameters["id"]
      ?: error("Callback redirect URL has no 'id' query param: $location")
    return TestPrincipal(token = token, scopedSystemId = systemId)
  } finally {
    client.close()
  }
}

internal data class TestPrincipal(
  val token: String,
  val scopedSystemId: String,
)

/** Filled by wasm browser tests when Gradle injects a JVM-minted token. */
internal var preMintedTestPrincipal: TestPrincipal? = null

private const val REDIRECT_COOKIE_NAME = "octocon_auth_redirect_uri"

private const val REDIRECT_PLACEHOLDER = "https://test.invalid/auth/done"
