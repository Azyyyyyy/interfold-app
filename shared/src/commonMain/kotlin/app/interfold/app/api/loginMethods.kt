package app.interfold.app.api

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
