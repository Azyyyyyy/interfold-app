package app.interfold.app.api

import app.interfold.app.utils.globalSerializer
import app.interfold.app.utils.ioDispatcher
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class LoginMethods(
  val cloudflare: Boolean = false,
  val google: Boolean = false,
  val discord: Boolean = false,
  val apple: Boolean = false,
)

enum class LoginMethodsStatus {
  Idle,
  Loading,
  Ready,
  Failed,
}

/**
 * Fetches `GET /auth/login-methods`.
 * @return methods plus whether the response was treated as an Access-gated challenge.
 */
suspend fun fetchLoginMethods(apiBaseUrl: String): Pair<LoginMethods, Boolean> =
  withContext(ioDispatcher) {
    val base = apiBaseUrl.trimEnd('/')
    try {
      val response = client.get("$base/auth/login-methods") {
        // Prefer JSON; Access challenges are usually HTML/redirects.
      }
      val body = response.bodyAsText()
      val location = response.headers[HttpHeaders.Location]
      if (looksLikeCloudflareAccessChallenge(response.status.value, location, body)) {
        return@withContext LoginMethods(cloudflare = true) to true
      }
      // Non-JSON success bodies (e.g. Access HTML served as 200) → treat as Access-on.
      val trimmed = body.trimStart()
      if (trimmed.startsWith("<") || (!trimmed.startsWith("{") && !trimmed.startsWith("["))) {
        return@withContext LoginMethods(cloudflare = true) to true
      }
      val parsed = globalSerializer.decodeFromString(LoginMethods.serializer(), body)
      parsed to false
    } catch (_: Exception) {
      LoginMethods() to false
    }
  }
