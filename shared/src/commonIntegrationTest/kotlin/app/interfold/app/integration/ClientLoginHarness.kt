package app.interfold.app.integration

import app.interfold.app.Settings
import app.interfold.app.ui.model.RootComponent
import app.interfold.app.ui.model.RootComponentImpl
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.utils.ColorSchemeParams
import app.interfold.app.utils.PlatformEvent
import app.interfold.app.utils.PlatformUtilities
import app.interfold.app.utils.WebURLOpenBehavior
import app.interfold.app.utils.platformUtilities
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Real [RootComponentImpl] + [LoginComponent] graph pointed at a live backend.
 * [openURL] is captured so the test can complete the browser OAuth hop.
 */
internal class ClientLoginHarness(baseUrl: String) : AutoCloseable {
  private val lifecycle = LifecycleRegistry()
  private val job = SupervisorJob()
  val openedUrl = CompletableDeferred<String>()
  val platformEvents = MutableSharedFlow<PlatformEvent>(extraBufferCapacity = 16)

  private val utilities = object : PlatformUtilities by platformUtilities {
    override fun saveSettings(settings: Settings) {
      // Do not write Java Preferences / platform stores from ITs.
    }

    override fun openURL(
      url: String,
      colorSchemeParams: ColorSchemeParams,
      webURLOpenBehavior: WebURLOpenBehavior,
    ) {
      openedUrl.complete(url)
    }
  }

  val root: RootComponent = runOnClientMainThread {
    RootComponentImpl(
      componentContext = DefaultComponentContext(lifecycle = lifecycle),
      initialSettings = Settings(apiEndpoint = baseUrl.trimEnd('/')),
      // Unconfined so LoginTokenReceived navigation runs on the emit thread (EDT).
      coroutineContext = Dispatchers.Unconfined + job,
      platformUtilities = utilities,
      platformEventFlow = platformEvents,
    ).also { lifecycle.resume() }
  }

  fun loginComponent() =
    (root.stack.value.active.instance as? RootComponent.Child.LoginChild)?.component
      ?: error("Expected Login child, was ${root.stack.value.active.instance}")

  fun deliverLoginToken(token: String) {
    runOnClientMainThread {
      check(platformEvents.tryEmit(PlatformEvent.LoginTokenReceived(token))) {
        "LoginTokenReceived was not delivered"
      }
    }
  }

  suspend fun awaitOpenedUrl(timeout: Duration = 15.seconds): String =
    withTimeout(timeout) { openedUrl.await() }

  override fun close() {
    runOnClientMainThread {
      (root.api as ApiInterfaceImpl).onDestroy()
      job.cancel()
      lifecycle.destroy()
    }
  }
}
