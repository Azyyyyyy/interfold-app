package app.interfold.app.ui.model

import app.interfold.app.BuildInfo
import app.interfold.app.api.LOGIN_METHODS_TIMEOUT_MS
import app.interfold.app.api.LoginMethods
import app.interfold.app.api.LoginMethodsStatus
import app.interfold.app.api.fetchLoginMethods
import app.interfold.app.api.isAccessJwtNearExpiry
import app.interfold.app.ui.compose.screens.IS_BETA
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.ui.registerStateHandler
import app.interfold.app.ui.retainStateHandler
import app.interfold.app.utils.ColorSchemeParams
import app.interfold.app.utils.DevicePlatform
import app.interfold.app.utils.WebURLOpenBehavior
import app.interfold.app.utils.buildRedirectUri
import app.interfold.app.utils.ioDispatcher
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

enum class ServerHealthStatus {
  UNKNOWN,
  CHECKING,
  HEALTHY,
  DEGRADED,
  UNREACHABLE
}

interface LoginComponent {
  val settings: SettingsInterface

  val model: StateFlow<Model>

  fun logInWithGoogle(colorSchemeParams: ColorSchemeParams)
  fun logInWithDiscord(colorSchemeParams: ColorSchemeParams)
  fun logInWithApple(colorSchemeParams: ColorSchemeParams)
  fun logInWithCloudflare(colorSchemeParams: ColorSchemeParams)

  fun updateServerUrl(url: String)
  fun checkServerHealth()
  fun fetchLoginMethods()

  @Serializable
  data class Model(
    val serverUrl: String = "",
    val serverHealthStatus: ServerHealthStatus = ServerHealthStatus.UNKNOWN,
    val loginMethods: LoginMethods = LoginMethods(),
    val loginMethodsStatus: LoginMethodsStatus = LoginMethodsStatus.Idle,
  )
}

internal data class HealthCheckResult(val readyUp: Boolean, val liveUp: Boolean)

internal expect suspend fun performHealthCheck(baseUrl: String): HealthCheckResult

private fun buildLoginUrl(provider: String, apiEndpoint: String): String =
  "$apiEndpoint/auth/${provider}" +
          "?platform=${DevicePlatform.internalName}" +
          "&version_code=${BuildInfo.VERSION_CODE}" +
          "&is_beta=${IS_BETA}" +
          "&redirect_uri=${buildRedirectUri("auth/token")}"

private fun buildCloudflareLoginUrl(apiEndpoint: String): String =
  "$apiEndpoint/auth/cloudflare?redirect_uri=${buildRedirectUri("auth/token")}"

internal class LoginComponentImpl(
  componentContext: CommonComponentContext
) : LoginComponent, CommonComponentContext by componentContext {
  private val handler = retainStateHandler { LoginComponent.Model(serverUrl = settings.data.value.apiEndpoint) }
  override val model = handler.model

  // Must precede `init`: fetchLoginMethods() uses these, and Kotlin runs init in
  // source order. Declaring them below left `scope` null and WASM `_start` trapped.
  private val scope = coroutineScope(coroutineContext + SupervisorJob())
  private var healthCheckJob: Job? = null
  private var loginMethodsJob: Job? = null

  init {
    registerStateHandler(handler)
    fetchLoginMethods()
  }

  override fun logInWithGoogle(colorSchemeParams: ColorSchemeParams) = logInWithProvider("google", colorSchemeParams)
  override fun logInWithDiscord(colorSchemeParams: ColorSchemeParams) = logInWithProvider("discord", colorSchemeParams)
  override fun logInWithApple(colorSchemeParams: ColorSchemeParams) = logInWithProvider("apple", colorSchemeParams)

  override fun logInWithCloudflare(colorSchemeParams: ColorSchemeParams) {
    val serverUrl = model.value.serverUrl.trimEnd('/')
    settings.setApiEndpoint(serverUrl)
    platformUtilities.openCloudflareAccessSession(
      url = buildCloudflareLoginUrl(serverUrl),
      apiBaseUrl = serverUrl,
      silent = false,
      onAccessJwt = { jwt -> settings.setCloudflareAccessJwt(jwt) },
      onFinished = {},
      onFailed = {},
    )
  }

  private fun logInWithProvider(provider: String, colorSchemeParams: ColorSchemeParams) {
    val serverUrl = model.value.serverUrl.trimEnd('/')
    settings.setApiEndpoint(serverUrl)
    platformUtilities.openURL(
      buildLoginUrl(provider, serverUrl),
      colorSchemeParams,
      webURLOpenBehavior = WebURLOpenBehavior.SameTab
    )
  }

  override fun updateServerUrl(url: String) {
    model.tryEmit(
      model.value.copy(
        serverUrl = url,
        serverHealthStatus = ServerHealthStatus.UNKNOWN,
        loginMethodsStatus = LoginMethodsStatus.Idle,
        loginMethods = LoginMethods(),
      )
    )
  }

  override fun checkServerHealth() {
    healthCheckJob?.cancel()
    val baseUrl = model.value.serverUrl.trimEnd('/')
    if (baseUrl.isBlank()) {
      markServerUnreachable()
      return
    }

    model.tryEmit(
      model.value.copy(
        serverHealthStatus = ServerHealthStatus.CHECKING,
        loginMethodsStatus = LoginMethodsStatus.Loading,
      )
    )

    healthCheckJob = scope.launch {
      try {
        val result = withContext(ioDispatcher) {
          withTimeout(LOGIN_METHODS_TIMEOUT_MS) { performHealthCheck(baseUrl) }
        }

        val status = when {
          result.readyUp && result.liveUp -> ServerHealthStatus.HEALTHY
          result.liveUp -> ServerHealthStatus.DEGRADED
          else -> ServerHealthStatus.UNREACHABLE
        }
        if (status == ServerHealthStatus.HEALTHY || status == ServerHealthStatus.DEGRADED) {
          model.tryEmit(model.value.copy(serverHealthStatus = status))
          fetchLoginMethods()
        } else {
          markServerUnreachable()
        }
      } catch (e: CancellationException) {
        if (e is TimeoutCancellationException) {
          markServerUnreachable()
        } else {
          throw e
        }
      } catch (_: Throwable) {
        markServerUnreachable()
      }
    }
  }

  override fun fetchLoginMethods() {
    loginMethodsJob?.cancel()
    val baseUrl = model.value.serverUrl.trimEnd('/')
    if (baseUrl.isBlank()) {
      model.tryEmit(
        model.value.copy(
          loginMethods = LoginMethods(),
          loginMethodsStatus = LoginMethodsStatus.Failed,
        )
      )
      return
    }

    model.tryEmit(model.value.copy(loginMethodsStatus = LoginMethodsStatus.Loading))
    loginMethodsJob = scope.launch {
      try {
        val (methods, _) = fetchLoginMethods(baseUrl)
        model.tryEmit(
          model.value.copy(
            loginMethods = methods,
            loginMethodsStatus = LoginMethodsStatus.Ready,
          )
        )
      } catch (e: CancellationException) {
        throw e
      } catch (_: Throwable) {
        model.tryEmit(
          model.value.copy(
            loginMethods = LoginMethods(),
            loginMethodsStatus = LoginMethodsStatus.Failed,
          )
        )
      }
    }
  }

  private fun markServerUnreachable() {
    loginMethodsJob?.cancel()
    model.tryEmit(
      model.value.copy(
        serverHealthStatus = ServerHealthStatus.UNREACHABLE,
        loginMethods = LoginMethods(),
        loginMethodsStatus = LoginMethodsStatus.Failed,
      )
    )
  }
}

/**
 * Foreground silent Cloudflare Access application-token rotation (pattern 2).
 * Single-flight; falls back to interactive Cloudflare login on failure.
 */
object CloudflareAccessSilentRefresh {
  private val mutex = Mutex()

  suspend fun refreshIfNeeded(
    settings: SettingsInterface,
    platformUtilities: app.interfold.app.utils.PlatformUtilities,
    force: Boolean = false,
  ): Boolean = mutex.withLock {
    val jwt = settings.data.value.cloudflareAccessJwt
    if (!force && !isAccessJwtNearExpiry(jwt)) return false

    val apiBase = settings.data.value.apiEndpoint.trimEnd('/')
    if (apiBase.isBlank()) return false

    var captured: String? = null
    var failed = false
    val done = kotlinx.coroutines.CompletableDeferred<Unit>()

    platformUtilities.openCloudflareAccessSession(
      url = apiBase,
      apiBaseUrl = apiBase,
      silent = true,
      onAccessJwt = { captured = it },
      onFinished = { done.complete(Unit) },
      onFailed = {
        failed = true
        done.complete(Unit)
      },
    )
    done.await()

    val newJwt = captured
    if (!failed && !newJwt.isNullOrBlank()) {
      settings.setCloudflareAccessJwt(newJwt)
      return true
    }

    // Escalate to interactive login URL so the user can re-auth through Access.
    val interactiveDone = kotlinx.coroutines.CompletableDeferred<Unit>()
    var interactiveJwt: String? = null
    platformUtilities.openCloudflareAccessSession(
      url = buildCloudflareLoginUrl(apiBase),
      apiBaseUrl = apiBase,
      silent = false,
      onAccessJwt = { interactiveJwt = it },
      onFinished = { interactiveDone.complete(Unit) },
      onFailed = { interactiveDone.complete(Unit) },
    )
    interactiveDone.await()
    interactiveJwt?.let { settings.setCloudflareAccessJwt(it) }
    !interactiveJwt.isNullOrBlank()
  }
}
