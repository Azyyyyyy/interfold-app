package app.interfold.app.ui.compose.screens.main.hometabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.interfold.app.Settings
import app.interfold.app.ui.compose.LocalNavigationType
import app.interfold.app.ui.compose.NavigationType
import app.interfold.app.ui.compose.theme.LocalInterShapes
import app.interfold.app.ui.compose.theme.LocalInterTypography
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.runBlocking
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.alters
import interfoldapp.shared.resources.friends
import interfoldapp.shared.resources.history
import interfoldapp.shared.resources.journal
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scaffold-only UI tests for [HomeTabsScreen]'s `BottomBar` and `NavigationRail`.
 *
 * These tests render the two bar composables in isolation against a hand-rolled
 * [FakeHomeTabsComponent]; they never compose the surrounding `HomeTabsScreen`
 * (which would render real tab bodies via `ChildStack`) so there's no need for
 * the inner tab components to do anything useful.
 *
 * Tab items are located via [Role.Tab] + the tab's visible text label (see the
 * `tab(...)` helper at the bottom of this file) — they carry no test tags in
 * production because Material's `NavigationBarItem` / `NavigationRailItem`
 * already expose role, label and selected state in the semantics tree. Only
 * the two bar containers themselves use [HomeTabsTestTags], because Material
 * exposes no distinguishing semantic on them. See the [HomeTabsTestTags]
 * KDoc for the rationale.
 *
 * The eight cases below cover the four guarantees the bar contracts make:
 *  - all four tabs render when the system is not a singlet
 *  - the bar highlights the tab matching the active `Child` reported by the component
 *  - tapping a non-selected tab fires the matching `navigateTo*` callback
 *  - tapping the already-selected tab does NOT fire `navigateTo*`
 *
 * Plus two singlet-vs-multi initial-tab cases at the bottom-bar level.
 */
@OptIn(ExperimentalTestApi::class)
class HomeTabsScaffoldTest {

  // -------- BottomBar ------------------------------------------------------------

  @Test
  fun bottomBar_rendersAllFourTabs_whenMultiSystem() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    onNodeWithTag(HomeTabsTestTags.BOTTOM_BAR).assertExists()
    tab(tabTitles.alters).assertExists()
    tab(tabTitles.history).assertExists()
    tab(tabTitles.journal).assertExists()
    tab(tabTitles.friends).assertExists()
  }

  @Test
  fun bottomBar_marksAltersSelected_whenCurrentScreenIsAltersChild() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.alters).assertIsSelected()
  }

  @Test
  fun bottomBar_marksFriendsSelected_whenCurrentScreenIsFriendsChild() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Friends)

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.friends).assertIsSelected()
  }

  @Test
  fun bottomBar_tappingNonSelectedTab_invokesNavigate() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.journal).performClick()

    assertEquals(1, fake.navigateToJournalCalls)
    assertEquals(0, fake.navigateToAltersCalls)
    assertEquals(0, fake.navigateToHistoryCalls)
    assertEquals(0, fake.navigateToFriendsCalls)
  }

  @Test
  fun bottomBar_tappingSelectedTab_doesNotInvokeNavigate() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.alters).performClick()

    assertEquals(0, fake.navigateToAltersCalls)
  }

  // -------- NavigationRail -------------------------------------------------------

  @Test
  fun navigationRail_rendersAllFourTabs_whenMultiSystem() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestNavigationRail(fake) } }

    onNodeWithTag(HomeTabsTestTags.NAVIGATION_RAIL).assertExists()
    tab(tabTitles.alters).assertExists()
    tab(tabTitles.history).assertExists()
    tab(tabTitles.journal).assertExists()
    tab(tabTitles.friends).assertExists()
  }

  @Test
  fun navigationRail_marksHistorySelected_whenCurrentScreenIsFrontHistoryChild() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.History)

    setContent { TestHomeTabsHost { TestNavigationRail(fake) } }

    tab(tabTitles.history).assertIsSelected()
  }

  @Test
  fun navigationRail_tappingNonSelectedTab_invokesNavigate() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Alters)

    setContent { TestHomeTabsHost { TestNavigationRail(fake) } }

    tab(tabTitles.friends).performClick()

    assertEquals(1, fake.navigateToFriendsCalls)
    assertEquals(0, fake.navigateToAltersCalls)
  }

  @Test
  fun navigationRail_tappingSelectedTab_doesNotInvokeNavigate() = runComposeUiTest {
    val fake = newFake(activeChild = FakeHomeTabsComponent.ActiveChild.Journal)

    setContent { TestHomeTabsHost { TestNavigationRail(fake) } }

    tab(tabTitles.journal).performClick()

    assertEquals(0, fake.navigateToJournalCalls)
  }

  // -------- Singlet vs multi-system initial tab ----------------------------------

  // Note: the production initial-stack rule lives in HomeTabsComponentImpl and is
  // exercised by a follow-up pure-component test (see the plan's "Open follow-ups").
  // These two cases lock in the *scaffold's* contract — that the bar reflects whichever
  // child the component reports as active — for both singlet and multi-system Settings.
  @Test
  fun bottomBar_defaultsToAltersSelected_whenIsSingletFalse() = runComposeUiTest {
    val fake = newFake(
      settings = Settings(isSinglet = false),
      activeChild = FakeHomeTabsComponent.ActiveChild.Alters
    )

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.alters).assertIsSelected()
  }

  @Test
  fun bottomBar_defaultsToFriendsSelected_whenIsSingletTrue() = runComposeUiTest {
    val fake = newFake(
      settings = Settings(isSinglet = true),
      activeChild = FakeHomeTabsComponent.ActiveChild.Friends
    )

    setContent { TestHomeTabsHost { TestBottomBar(fake) } }

    tab(tabTitles.friends).assertIsSelected()
  }

  // -------- Full HomeTabsScreen composition --------------------------------------

  // These three exercise HomeTabsScreen end-to-end: the bar branch (or its absence)
  // is selected by the screen itself from Settings + LocalNavigationType, not by
  // the test calling BottomBar/NavigationRail directly. renderChild is supplied as
  // an empty composable so we don't need real Alters/FrontHistory/Journal/Friends
  // screens — only the scaffold contract is under test here.
  @Test
  fun homeTabsScreen_marksAltersSelected_inBottomBar_whenMultiSystem() = runComposeUiTest {
    val fake = newFake(
      settings = Settings(isSinglet = false),
      activeChild = FakeHomeTabsComponent.ActiveChild.Alters
    )

    setContent { TestHomeTabsHost { TestHomeTabsScreen(fake, NavigationType.BOTTOM_BAR) } }

    onNodeWithTag(HomeTabsTestTags.BOTTOM_BAR).assertExists()
    tab(tabTitles.alters).assertIsSelected()
  }

  @Test
  fun homeTabsScreen_marksAltersSelected_inNavigationRail_whenMultiSystem() = runComposeUiTest {
    val fake = newFake(
      settings = Settings(isSinglet = false),
      activeChild = FakeHomeTabsComponent.ActiveChild.Alters
    )

    setContent { TestHomeTabsHost { TestHomeTabsScreen(fake, NavigationType.RAIL) } }

    onNodeWithTag(HomeTabsTestTags.NAVIGATION_RAIL).assertExists()
    tab(tabTitles.alters).assertIsSelected()
  }

  @Test
  fun homeTabsScreen_hidesBothBars_whenIsSingletTrue() = runComposeUiTest {
    val fake = newFake(
      settings = Settings(isSinglet = true),
      activeChild = FakeHomeTabsComponent.ActiveChild.Friends
    )

    // Singlet systems should see neither bar regardless of which navigation type
    // the host would normally use, so pick BOTTOM_BAR (the mobile default) and
    // assert both the bottom bar and the navigation rail tags are absent.
    setContent { TestHomeTabsHost { TestHomeTabsScreen(fake, NavigationType.BOTTOM_BAR) } }

    onNodeWithTag(HomeTabsTestTags.BOTTOM_BAR).assertDoesNotExist()
    onNodeWithTag(HomeTabsTestTags.NAVIGATION_RAIL).assertDoesNotExist()
  }
}

// -------- Test helpers ------------------------------------------------------------

/**
 * Captured visible labels for the four tabs, resolved once via the non-composable
 * `getString` so tests always reference what the *current locale* displays rather
 * than hardcoded English literals. Resolution is suspending, so we cross the
 * boundary with [runBlocking] in a top-level `lazy` — the test process only does
 * this once.
 */
private data class TabTitles(
  val alters: String,
  val history: String,
  val journal: String,
  val friends: String,
)

private val tabTitles: TabTitles by lazy {
  runBlocking {
    TabTitles(
      alters = getString(Res.string.alters),
      history = getString(Res.string.history),
      journal = getString(Res.string.journal),
      friends = getString(Res.string.friends),
    )
  }
}

/**
 * Matches any semantics node whose `Role` is [Role.Tab]. Material's
 * `NavigationBarItem` and `NavigationRailItem` both set this internally via
 * `Modifier.selectable(role = Role.Tab, ...)`, so the four tab items are
 * locatable without any production-side test tag. See
 * https://proandroiddev.com/stop-using-test-tags-in-the-jetpack-compose-production-code-b98e2679221f
 * for the rationale.
 */
@OptIn(ExperimentalTestApi::class)
private val TabRoleMatcher: SemanticsMatcher =
  SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/**
 * Locates a single tab by its visible text label, combining [TabRoleMatcher]
 * with [hasText] so the matcher cannot accidentally bind to any other node
 * that happens to expose the same text.
 */
@OptIn(ExperimentalTestApi::class)
private fun SemanticsNodeInteractionsProvider.tab(title: String): SemanticsNodeInteraction =
  onNode(TabRoleMatcher and hasText(title))

private fun newFake(
  settings: Settings = Settings(),
  activeChild: FakeHomeTabsComponent.ActiveChild = FakeHomeTabsComponent.ActiveChild.Alters
): FakeHomeTabsComponent = FakeHomeTabsComponent(
  initialSettings = settings,
  initialActiveChild = activeChild
)

/**
 * Minimal Inter theme stand-in: production [BottomBar]/[NavigationRail] expect
 * `LocalInterTypography` and `LocalInterShapes` to be provided, and live inside a
 * `MaterialTheme`. The real `InterTheme` pulls platform font resources and
 * animations we don't want in a scaffold test, so we wire just enough here.
 */
@Composable
private fun TestHomeTabsHost(content: @Composable () -> Unit) {
  MaterialTheme {
    CompositionLocalProvider(
      LocalInterTypography provides MaterialTheme.typography,
      LocalInterShapes provides MaterialTheme.shapes
    ) {
      content()
    }
  }
}

@Composable
private fun TestBottomBar(fake: FakeHomeTabsComponent) {
  val settings by fake.fakeSettings.data.collectAsState()
  val stack by fake.stack.subscribeAsState()
  BottomBar(
    settings = settings,
    component = fake,
    lazyListCoroutineScope = rememberCoroutineScope(),
    lazyListState = null,
    currentScreen = stack.active.instance,
    color = null
  )
}

@Composable
private fun TestNavigationRail(fake: FakeHomeTabsComponent) {
  val settings by fake.fakeSettings.data.collectAsState()
  val stack by fake.stack.subscribeAsState()
  NavigationRail(
    settings = settings,
    component = fake,
    lazyListCoroutineScope = rememberCoroutineScope(),
    lazyListState = null,
    currentScreen = stack.active.instance,
    color = null,
    toggleDrawer = {}
  )
}

/**
 * Composes the full [HomeTabsScreenContent] against a [FakeHomeTabsComponent],
 * providing [LocalNavigationType] (which the screen reads from the parent in
 * production via `MainAppScreen`) and an empty `renderChild` so we don't need
 * real tab screens. The public `HomeTabsScreen` doesn't expose `renderChild`
 * — the `internal` content overload does, and that's what `commonTest`
 * reaches for here. `HomeTabsScreenContent` provides `LocalFABIsCollapsed`
 * and `LocalUpdateLazyListState` itself, so nothing else has to be wired.
 */
@Composable
private fun TestHomeTabsScreen(
  fake: FakeHomeTabsComponent,
  navigationType: NavigationType
) {
  CompositionLocalProvider(LocalNavigationType provides navigationType) {
    HomeTabsScreenContent(component = fake, renderChild = {})
  }
}
