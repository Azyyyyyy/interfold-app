package app.interfold.app.integration

import app.interfold.app.api.APIState
import app.interfold.app.api.LoginMethodsStatus
import app.interfold.app.api.model.MySystem
import app.interfold.app.utils.ColorSchemeParams
import app.interfold.app.utils.buildRedirectUri
import app.interfold.app.utils.ioDispatcher
import io.ktor.client.request.cookie
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Live-backend Google login through the real client:
 * [LoginComponent.logInWithGoogle] → captured [openURL] → mock Google authorize
 * → `/auth/google/callback` → `interfold://auth/token` → [PlatformEvent.LoginTokenReceived]
 * → [RootComponent.tryLoadClient].
 */
internal suspend fun assertGoogleLoginExchangesCodeOverMock(baseUrl: String) {
  ClientLoginHarness(baseUrl).use { harness ->
    val login = harness.loginComponent()
    val methods = withContext(ioDispatcher) {
      withTimeout(15.seconds) {
        login.model.first {
          it.loginMethodsStatus == LoginMethodsStatus.Ready && it.loginMethods.google
        }.loginMethods
      }
    }
    assertTrue(methods.google, "LoginComponent should discover Google from /auth/login-methods")

    login.logInWithGoogle(ColorSchemeParams(null, null, null, null))
    val opened = harness.awaitOpenedUrl()
    assertTrue(
      opened.startsWith("${baseUrl.trimEnd('/')}/auth/google"),
      "LoginComponent.openURL should start the Google challenge, got $opened",
    )
    val clientRedirect = buildRedirectUri("auth/token")
    assertTrue(
      opened.contains(clientRedirect) || opened.contains(encodeQuery(clientRedirect)),
      "Challenge URL should carry the client's $clientRedirect redirect_uri, got $opened",
    )

    val http = createNoRedirectHttpClient()
    try {
      val begin = http.get(opened)
      check(begin.status.value in 300..399) {
        "Expected 30x from client-opened /auth/google, got ${begin.status}: ${begin.bodyAsText()}"
      }
      val authorizeLocation = begin.headers[HttpHeaders.Location]
        ?: error("/auth/google missing Location")
      assertTrue(
        authorizeLocation.startsWith("https://accounts.google.com/o/oauth2/v2/auth"),
        "Challenge should point at Google authorize, got $authorizeLocation",
      )

      val callbackUrl = mockGoogleAuthorizeCallback(
        authorizeLocation = authorizeLocation,
        callbackOrigin = baseUrl,
      )
      val callback = http.get(callbackUrl) {
        cookie(name = REDIRECT_COOKIE_NAME, value = clientRedirect)
      }
      check(callback.status.value in 300..399) {
        "Expected 30x from Google callback, got ${callback.status}: ${callback.bodyAsText()}"
      }
      val done = callback.headers[HttpHeaders.Location]
        ?: error("Google callback missing Location")
      val parsed = URLBuilder(done).build()
      val token = parsed.parameters["token"]
        ?: error("Deep-link redirect has no token: $done")
      val systemId = parsed.parameters["id"]
        ?: error("Deep-link redirect has no id: $done")
      assertTrue(
        done.startsWith(clientRedirect),
        "Callback should redirect to the client deep link $clientRedirect, got $done",
      )

      harness.deliverLoginToken(token)
      withContext(ioDispatcher) {
        withTimeout(10.seconds) {
          harness.root.settings.data.first { it.token == token }
        }
      }

      ensureUserExists(baseUrl, token)
      harness.root.tryLoadClient(token)
      withContext(ioDispatcher) {
        withTimeout(30.seconds) {
          harness.root.api.initComplete.first { it }
        }
      }
      assertTrue(harness.root.api.systemMe.value is APIState.Success)
      val me = (harness.root.api.systemMe.value as APIState.Success<MySystem>).data
      assertEquals(systemId, me.id)
    } finally {
      http.close()
    }
  }
}

private fun encodeQuery(value: String): String =
  buildString {
    value.forEach { ch ->
      when (ch) {
        ' ' -> append("%20")
        ':' -> append("%3A")
        '/' -> append("%2F")
        else -> append(ch)
      }
    }
  }
