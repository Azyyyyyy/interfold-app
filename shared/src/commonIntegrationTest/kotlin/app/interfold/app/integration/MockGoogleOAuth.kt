package app.interfold.app.integration

import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * Test-side stand-in for Google's authorize hop. The API still 302s to
 * `accounts.google.com`; we never follow that host. Instead we read
 * `redirect_uri` / `state` from the challenge Location and build the
 * callback the browser would land on after a successful consent screen.
 *
 * Identity is the API's existing `email` query fallback — no token
 * exchange, so the container never calls `oauth2.googleapis.com`.
 */
internal fun mockGoogleAuthorizeCallback(
  authorizeLocation: String,
  callbackOrigin: String,
  email: String = TEST_GOOGLE_EMAIL,
  code: String = "test-google-code",
): String {
  val authorize = Url(authorizeLocation)
  val redirectUri = authorize.parameters["redirect_uri"]
    ?: error("Google authorize Location missing redirect_uri: $authorizeLocation")
  val redirect = Url(redirectUri)
  check(redirect.encodedPath.endsWith("/auth/google/callback")) {
    "Expected Google redirect_uri to be /auth/google/callback, got $redirectUri"
  }

  val origin = callbackOrigin.trimEnd('/')
  val callback = URLBuilder("$origin${redirect.encodedPath}")
  callback.parameters["code"] = code
  callback.parameters["email"] = email
  val state = authorize.parameters["state"]
  if (!state.isNullOrBlank()) {
    callback.parameters["state"] = state
  }
  return callback.buildString()
}
