package app.interfold.app.ui.compose.screens.main.hometabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups2
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.interfold.app.Settings
import app.interfold.app.ui.compose.LocalFABIsCollapsed
import app.interfold.app.ui.compose.LocalModalDrawerToggler
import app.interfold.app.ui.compose.LocalNavigationType
import app.interfold.app.ui.compose.LocalUpdateLazyListState
import app.interfold.app.ui.compose.NavigationType
import app.interfold.app.ui.compose.theme.ThemeFromColor
import app.interfold.app.ui.model.main.hometabs.HomeTabsComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.derive
import app.interfold.app.utils.state
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.experimental.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.alters
import interfoldapp.shared.resources.friends
import interfoldapp.shared.resources.history
import interfoldapp.shared.resources.journal
import interfoldapp.shared.resources.menu

/**
 * Test-only [Modifier.testTag] identifiers for the [ShortNavigationBar] and
 * [NavigationRail] containers rendered by this screen.
 *
 * These two tags exist purely so unit tests can disambiguate "the bottom
 * bar is showing" from "the navigation rail is showing". They have no
 * accessibility purpose — Material's bar/rail containers themselves are
 * not announced by assistive tech (TalkBack, VoiceOver); only their child
 * tab items are, and those are located via [androidx.compose.ui.semantics.Role.Tab]
 * + the tab's visible label, not via test tags. See the four tab items in
 * this file for the article-recommended approach
 * (https://proandroiddev.com/stop-using-test-tags-in-the-jetpack-compose-production-code-b98e2679221f):
 * we deliberately do NOT tag them, because their `Role.Tab` + text label
 * + `selected` state already give tests and a11y tooling everything they
 * need.
 *
 * The bars are the article's "inevitable" exception: Material exposes no
 * distinguishing role or content description on the containers themselves,
 * so the only signal that disambiguates them in a structural test is a tag
 * applied here. If Material ever ships such a role, drop these too.
 */
object HomeTabsTestTags {
  const val BOTTOM_BAR = "homeTabs.bottomBar"
  const val NAVIGATION_RAIL = "homeTabs.navigationRail"
}

/**
 * Production entry point for the home-tabs screen. Wraps [HomeTabsScreenContent]
 * with the default child renderer so callers don't have to think about the
 * test seam. The `internal` overload is what `commonTest` reaches for when it
 * wants to compose the scaffold without wiring real tab screens.
 */
@Composable
fun HomeTabsScreen(component: HomeTabsComponent) {
  HomeTabsScreenContent(component) { DefaultHomeTabsChild(it) }
}

/**
 * The actual screen body. Kept `internal` so production cannot accidentally
 * override the child renderer (only one caller in the app —
 * [app.interfold.app.ui.compose.screens.main.MainAppScreen] — and it uses the
 * public [HomeTabsScreen]), while `commonTest` can still inject an empty
 * renderer to exercise the scaffold without `AltersScreen` / `FrontHistoryScreen`
 * / `JournalScreen` / `FriendsScreen` and their full state graphs.
 */
@OptIn(ExperimentalDecomposeApi::class)
@Composable
internal fun HomeTabsScreenContent(
  component: HomeTabsComponent,
  renderChild: @Composable (HomeTabsComponent.Child) -> Unit
) {
  /*val topAppBarState = rememberTopAppBarState()
  val scrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)*/

  val settings: Settings by component.settings.collectAsState()

  val isSinglet by derive { settings.isSinglet }

  val stack by component.stack.subscribeAsState()
  val currentScreen = stack.active.instance

  // TODO: Dynamic scaffold color
  /*var scaffoldColorMap by state(linkedMapOf<Any, String?>())
  val currentColor by derive { scaffoldColorMap.values.lastOrNull() }

  LaunchedEffect(scaffoldColorMap) {
    println("Scaffold color map: $scaffoldColorMap")
  }

  LaunchedEffect(currentColor) {
    println("Current color: $currentColor")
  }*/

  // val screenTransitionType by derive { settings.screenTransitionType }

  val lazyListCoroutineScope = rememberCoroutineScope()
  var lazyListState: LazyListState? by state(null)

  val updateLazyListState: (LazyListState?) -> Unit = {
    lazyListState = it
  }

  LaunchedEffect(Unit) {
    component.updateOnCurrentTabPressed {
      lazyListState?.let {
        lazyListCoroutineScope.launch {
          it.animateScrollToItem(0)
        }
      }
    }
  }

  var bottomBarIsCollapsed by state(false)
  val bottomBarNestedScrollConnection = remember {
    object : NestedScrollConnection {
      var isFling = false

      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Hide bottom bar & FAB
        if (available.y < -5) {
          bottomBarIsCollapsed = true
        }

        // Show bottom bar & FAB
        if (available.y > 5) {
          bottomBarIsCollapsed = false
        }

        return Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        isFling = true
        return super.onPreFling(available)
      }

      override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity
      ): Velocity {
        isFling = false
        return super.onPostFling(consumed, available)
      }
    }
  }

  val navigationType = LocalNavigationType.current

  Box(
    modifier = Modifier.nestedScroll(bottomBarNestedScrollConnection).fillMaxSize()
  ) {
    CompositionLocalProvider(
      LocalFABIsCollapsed provides bottomBarIsCollapsed,
      LocalUpdateLazyListState provides updateLazyListState
    ) {
      Row(modifier = Modifier.fillMaxSize()) {
        if (!isSinglet && navigationType == NavigationType.RAIL) {
          // Use dual panels when on a tablet-sized screen
          NavigationRail(
            settings,
            component,
            lazyListCoroutineScope,
            lazyListState,
            currentScreen,
            null,
            LocalModalDrawerToggler.current,
            modifier = Modifier.zIndex(999f)
          )
        }

        ChildStack(
          stack,
          animation = stackAnimation(fade(tween(200))),
          modifier = Modifier.fillMaxSize()
        ) {
          renderChild(it.instance)
        }
      }
    }
    if(!isSinglet && navigationType == NavigationType.BOTTOM_BAR) {
      // Use navigation bar when on a narrower screen
      AnimatedContent(
        targetState = bottomBarIsCollapsed,
        transitionSpec = {
          slideInVertically { height -> height } togetherWith
              slideOutVertically { height -> height }
        },
        modifier = Modifier.align(Alignment.BottomStart)
      ) { isCollapsed ->
        if (isCollapsed) {
          Box(modifier = Modifier.fillMaxWidth().height(0.dp))
        } else {
          BottomBar(
            settings,
            component,
            lazyListCoroutineScope,
            lazyListState,
            currentScreen,
            null
          )
        }
      }
    }
  }
}

@Composable
private fun DefaultHomeTabsChild(child: HomeTabsComponent.Child) {
  when (child) {
    is HomeTabsComponent.Child.AltersChild -> AltersScreen(child.component)
    is HomeTabsComponent.Child.FrontHistoryChild -> FrontHistoryScreen(child.component)
    is HomeTabsComponent.Child.JournalChild -> JournalScreen(child.component)
    is HomeTabsComponent.Child.FriendsChild -> FriendsScreen(child.component)
  }
}

@Composable
fun NavigationRail(
  settings: Settings,
  component: HomeTabsComponent,
  lazyListCoroutineScope: CoroutineScope,
  lazyListState: LazyListState?,
  currentScreen: HomeTabsComponent.Child,
  color: String?,
  toggleDrawer: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  ThemeFromColor(
    color,
    colorMode = settings.colorMode,
    dynamicColorType = settings.dynamicColorType,
    colorContrastLevel = settings.colorContrastLevel,
    amoledMode = settings.amoledMode,
    reduceMotion = settings.reduceMotion
  ) {
    NavigationRail(
      modifier = modifier.testTag(HomeTabsTestTags.NAVIGATION_RAIL),
      header = {
        IconButton(
          onClick = { toggleDrawer(true) }
        ) {
          Icon(
            Icons.Rounded.Menu,
            contentDescription = Res.string.menu.compose
          )
        }
      }
    ) {
      Spacer(modifier = Modifier.weight(1f))
      InterNavigationRailItem(
        Res.string.alters.compose,
        rememberVectorPainter(Icons.Rounded.Groups2),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.AltersChild,
        component::navigateToAlters
      )
      InterNavigationRailItem(
        Res.string.history.compose,
        rememberVectorPainter(Icons.Rounded.History),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.FrontHistoryChild,
        component::navigateToHistory
      )
      InterNavigationRailItem(
        Res.string.journal.compose,
        rememberVectorPainter(Icons.Rounded.Book),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.JournalChild,
        component::navigateToJournal
      )
      InterNavigationRailItem(
        Res.string.friends.compose,
        rememberVectorPainter(Icons.Rounded.Favorite),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.FriendsChild,
        component::navigateToFriends
      )
      Spacer(modifier = Modifier.weight(1f))
    }
  }
}


@Composable
fun BottomBar(
  settings: Settings,
  component: HomeTabsComponent,
  lazyListCoroutineScope: CoroutineScope,
  lazyListState: LazyListState?,
  currentScreen: HomeTabsComponent.Child,
  color: String?,
  modifier: Modifier = Modifier
) {
  ThemeFromColor(
    color,
    colorMode = settings.colorMode,
    dynamicColorType = settings.dynamicColorType,
    colorContrastLevel = settings.colorContrastLevel,
    amoledMode = settings.amoledMode,
    reduceMotion = settings.reduceMotion
  ) {
    ShortNavigationBar(
      // windowInsets = WindowInsets.navigationBars,
      modifier = modifier.testTag(HomeTabsTestTags.BOTTOM_BAR)
    ) {
      InterNavigationBarItem(
        Res.string.alters.compose,
        rememberVectorPainter(Icons.Rounded.Groups2),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.AltersChild,
        component::navigateToAlters
      )
      InterNavigationBarItem(
        Res.string.history.compose,
        rememberVectorPainter(Icons.Rounded.History),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.FrontHistoryChild,
        component::navigateToHistory
      )
      InterNavigationBarItem(
        Res.string.journal.compose,
        rememberVectorPainter(Icons.Rounded.Book),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.JournalChild,
        component::navigateToJournal
      )
      InterNavigationBarItem(
        Res.string.friends.compose,
        rememberVectorPainter(Icons.Rounded.Favorite),
        lazyListCoroutineScope,
        lazyListState,
        currentScreen is HomeTabsComponent.Child.FriendsChild,
        component::navigateToFriends
      )
    }
  }
}


@Composable
private fun InterNavigationBarItem(
  title: String,
  icon: VectorPainter,
  lazyListCoroutineScope: CoroutineScope,
  lazyListState: LazyListState?,
  isSelected: Boolean,
  navigate: () -> Unit
) {
  ShortNavigationBarItem(
    selected = isSelected,
    onClick = {
      if (isSelected) {
        lazyListState?.let {
          lazyListCoroutineScope.launch {
            it.animateScrollToItem(0)
          }
        }
      } else {
        navigate()
      }
    },
    icon = { Icon(painter = icon, contentDescription = title) },
    label = { Text(text = title) }
  )
}

@Composable
private fun ColumnScope.InterNavigationRailItem(
  title: String,
  icon: VectorPainter,
  lazyListCoroutineScope: CoroutineScope,
  lazyListState: LazyListState?,
  isSelected: Boolean,
  navigate: () -> Unit
) {
  NavigationRailItem(
    selected = isSelected,
    onClick = {
      if(isSelected) {
        lazyListState?.let {
          lazyListCoroutineScope.launch {
            it.animateScrollToItem(0)
          }
        }
      } else {
        navigate()
      }
    },
    icon = { Icon(painter = icon, contentDescription = title) },
    label = { Text(text = title) }
  )
}