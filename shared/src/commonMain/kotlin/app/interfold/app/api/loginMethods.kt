package app.interfold.app.api

import app.interfold.app.utils.globalSerializer
import kotlinx.serialization.Serializable

@Serializable
data class LoginMethods(
  val cloudflare: Boolean = false,
  val google: Boolean = false,
  val discord: Boolean = false,
  val apple: Boolean = false,
) {
  val hasAny: Boolean
    get() = cloudflare || google || discord || apple
}

enum class LoginMethodsStatus {
  Idle,
  Loading,
  Ready,
  Failed,
}

internal const val LOGIN_METHODS_TIMEOUT_MS = 15_000L

internal fun parseLoginMethodsResponse(
  statusCode: Int,
  locationHeader: String?,
  body: String,
): Pair<LoginMethods, Boolean> {
  if (looksLikeCloudflareAccessChallenge(statusCode, locationHeader, body)) {
    return LoginMethods(cloudflare = true) to true
  }
  if (statusCode !in 200..299) {
    error("login-methods HTTP $statusCode")
  }
  val trimmed = body.trimStart()
  if (trimmed.startsWith("<")) {
    return LoginMethods(cloudflare = true) to true
  }
  return globalSerializer.decodeFromString(LoginMethods.serializer(), body) to false
}

