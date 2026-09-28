package app.interfold.app.ui.model

import app.interfold.app.Settings
import app.interfold.app.api.FakePhoenixSocketSessionFactory
import app.interfold.app.api.LoginMethodsStatus
import app.interfold.app.ui.compose.screens.main.hometabs.FakeSettingsInterface
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.utils.failingPlatformUtilities
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression for the WASM `_start` null-deref: PR 27 called [LoginComponentImpl.fetchLoginMethods]
 * from `init` while `scope` was still uninitialized. A blank endpoint returns before
 * `scope.launch` and would not catch the bug, so this uses a non-routable URL.
 */
class LoginComponentInitTest {

  @Test
  fun constructs_withNonBlankEndpoint_withoutThrowing() {
    val lifecycle = LifecycleRegistry()
    val rootCtx = DefaultComponentContext(lifecycle = lifecycle)
    val apiScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val componentJob = SupervisorJob()
    val swallowFetchErrors = CoroutineExceptionHandler { _, _ -> }
    val settings = FakeSettingsInterface(
      Settings(apiEndpoint = "http://127.0.0.1:1")
    )
    val api = ApiInterfaceImpl(
      coroutineScope = apiScope,
      platformUtilities = failingPlatformUtilities(),
      settingsInterface = settings,
      socketSessionFactory = FakePhoenixSocketSessionFactory(),
    )
    val commonCtx = CommonComponentContextImpl(
      componentContext = rootCtx,
      api = api,
      settings = settings,
      platformUtilities = api.platformUtilities,
      coroutineContext = Dispatchers.Unconfined + componentJob + swallowFetchErrors,
    )

    try {
      val component = LoginComponentImpl(commonCtx)

      assertEquals("http://127.0.0.1:1", component.model.value.serverUrl)
      assertNotEquals(LoginMethodsStatus.Idle, component.model.value.loginMethodsStatus)
      assertTrue(
        component.model.value.loginMethodsStatus == LoginMethodsStatus.Loading
          || component.model.value.loginMethodsStatus == LoginMethodsStatus.Failed
          || component.model.value.loginMethodsStatus == LoginMethodsStatus.Ready,
        "init must reach fetchLoginMethods(); status=${component.model.value.loginMethodsStatus}",
      )
    } finally {
      componentJob.cancel()
      apiScope.cancel()
    }
  }
}
