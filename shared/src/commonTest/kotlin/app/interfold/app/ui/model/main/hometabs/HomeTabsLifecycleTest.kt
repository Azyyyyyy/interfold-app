package app.interfold.app.ui.model.main.hometabs

import app.interfold.app.Settings
import app.interfold.app.api.FakePhoenixSocketSessionFactory
import app.interfold.app.ui.compose.screens.main.hometabs.FakeSettingsInterface
import app.interfold.app.ui.model.CommonComponentContextImpl
import app.interfold.app.ui.model.MainComponentContextImpl
import app.interfold.app.ui.model.interfaces.ApiInterfaceImpl
import app.interfold.app.utils.FailingPlatformUtilities
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import com.arkivanov.essenty.lifecycle.start
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full-integration test of [HomeTabsComponentImpl]'s lifecycle subscription.
 *
 * The component's `init` block subscribes a [com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks]
 * whose `onStart` / `onResume` both call `navigateToFriendsIfNecessary`, which fires
 * `navigationWrapper(Config.Friends)` only when `settings.isSinglet` is `true`.
 *
 * Each test wires up a real component graph against the real [ApiInterfaceImpl] (driven by a
 * [FakePhoenixSocketSessionFactory], so no backend is needed) plus fake `Settings` and
 * `PlatformUtilities` implementations, drives the lifecycle forward via Essenty's manual API,
 * and asserts on the navigation callbacks captured by the harness.
 *
 * `LifecycleRegistry()` constructs in [com.arkivanov.essenty.lifecycle.Lifecycle.State.INITIALIZED].
 * The extension functions are additive:
 *   - `start()` walks INITIALIZED → CREATED → STARTED, firing one `onStart`.
 *   - `resume()` walks INITIALIZED → CREATED → STARTED → RESUMED, firing one `onStart` plus
 *     one `onResume` (so two callbacks in the singlet case).
 */
class HomeTabsLifecycleTest {

  @Test
  fun lifecycleStart_navigatesToFriendsOnce_whenSinglet() {
    val h = newHomeTabs(isSinglet = true)
    h.lifecycle.start()
    assertEquals(listOf(HomeTabsComponentImpl.Config.Friends), h.navigations)
  }

  @Test
  fun lifecycleResume_navigatesToFriendsOnStartAndResume_whenSinglet() {
    val h = newHomeTabs(isSinglet = true)
    h.lifecycle.resume()
    assertEquals(
      listOf(HomeTabsComponentImpl.Config.Friends, HomeTabsComponentImpl.Config.Friends),
      h.navigations
    )
  }

  @Test
  fun lifecycleResume_doesNotNavigate_whenNotSinglet() {
    val h = newHomeTabs(isSinglet = false)
    h.lifecycle.resume()
    assertTrue(
      h.navigations.isEmpty(),
      "Expected no navigation when system is not a singlet, captured: ${h.navigations}"
    )
  }

  private class Harness(
    @Suppress("unused") val component: HomeTabsComponentImpl,
    val navigations: List<HomeTabsComponentImpl.Config>,
    val lifecycle: LifecycleRegistry,
  )

  private fun newHomeTabs(isSinglet: Boolean): Harness {
    val lifecycle = LifecycleRegistry()
    val rootCtx = DefaultComponentContext(lifecycle = lifecycle)
    // Real ApiInterfaceImpl wired to a fake socket factory. The lifecycle path
    // never calls loadClient, so the factory's create() is never invoked. If a
    // future change accidentally introduces an API call from the lifecycle
    // path, the fake's loud-fail defaults will surface it immediately.
    val apiScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    val api = ApiInterfaceImpl(
      coroutineScope = apiScope,
      platformUtilities = FailingPlatformUtilities(),
      settingsInterface = FakeSettingsInterface(Settings(isSinglet = isSinglet)),
      socketSessionFactory = FakePhoenixSocketSessionFactory(),
    )
    val commonCtx = CommonComponentContextImpl(
      componentContext = rootCtx,
      api = api,
      settings = api.settingsInterface,
      platformUtilities = api.platformUtilities,
      coroutineContext = Dispatchers.Unconfined,
    )
    val mainCtx = MainComponentContextImpl(commonCtx)
    val captured = mutableListOf<HomeTabsComponentImpl.Config>()
    val component = HomeTabsComponentImpl(
      componentContext = mainCtx,
      navigator = StackNavigation(),
      navigationWrapper = { captured += it },
      navigateToCustomFieldsFun = {},
    )
    return Harness(component = component, navigations = captured, lifecycle = lifecycle)
  }
}
