package app.interfold.app.ui.compose.screens.main.hometabs

import app.interfold.app.Settings
import app.interfold.app.api.APIState
import app.interfold.app.api.model.MyAlter
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.ui.model.main.hometabs.FrontHistoryComponent
import app.interfold.app.ui.model.main.hometabs.HomeTabsComponent
import app.interfold.app.utils.MonthYearPair
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.StateFlow

/**
 * Hand-rolled test double of [HomeTabsComponent] suitable for scaffold-only tests.
 *
 * The fake exposes a mutable [ChildStack] so tests can drive which tab is "active",
 * and counts each `navigateTo*` call so tests can assert intent. The inner tab
 * components are stubs — `BottomBar` and `NavigationRail` only do `is Child.X`
 * type checks, so the stubs are never read. Alters/Friends/Journal stubs are
 * [stubAltersComponent] expect/actuals because a Kotlin `object : AltersComponent`
 * (etc.) ICEs the Kotlin/Wasm compiler; see that function's KDoc.
 */
class FakeHomeTabsComponent(
  initialSettings: Settings = Settings(),
  initialActiveChild: ActiveChild = ActiveChild.Alters
) : HomeTabsComponent {

  val fakeSettings: FakeSettingsInterface = FakeSettingsInterface(initialSettings)
  override val settings: SettingsInterface = fakeSettings

  private val _stack: MutableValue<ChildStack<Any, HomeTabsComponent.Child>> =
    MutableValue(buildStack(initialActiveChild))
  override val stack: Value<ChildStack<*, HomeTabsComponent.Child>> = _stack

  var navigateToAltersCalls: Int = 0
    private set
  var navigateToHistoryCalls: Int = 0
    private set
  var navigateToFriendsCalls: Int = 0
    private set
  var navigateToJournalCalls: Int = 0
    private set

  override fun navigateToAlters() {
    navigateToAltersCalls++
  }

  override fun navigateToHistory() {
    navigateToHistoryCalls++
  }

  override fun navigateToFriends() {
    navigateToFriendsCalls++
  }

  override fun navigateToJournal() {
    navigateToJournalCalls++
  }

  override var onCurrentTabPressed: (() -> Unit)? = null
    private set

  override fun updateOnCurrentTabPressed(onCurrentTabPressed: () -> Unit) {
    this.onCurrentTabPressed = onCurrentTabPressed
  }

  /** Updates which child the [stack] reports as active, e.g. between assertions. */
  fun setActiveChild(active: ActiveChild) {
    _stack.value = buildStack(active)
  }

  private fun buildStack(active: ActiveChild): ChildStack<Any, HomeTabsComponent.Child> =
    ChildStack(
      configuration = active,
      instance = active.toChild()
    )

  enum class ActiveChild {
    Alters, History, Journal, Friends;

    internal fun toChild(): HomeTabsComponent.Child = when (this) {
      Alters -> HomeTabsComponent.Child.AltersChild(stubAltersComponent())
      History -> HomeTabsComponent.Child.FrontHistoryChild(StubFrontHistoryComponent)
      Journal -> HomeTabsComponent.Child.JournalChild(stubJournalComponent())
      Friends -> HomeTabsComponent.Child.FriendsChild(stubFriendsComponent())
    }
  }
}

private fun unused(): Nothing =
  error("Inner tab component is not used in scaffold-only tests")

private object StubFrontHistoryComponent : FrontHistoryComponent {
  override val settings: SettingsInterface get() = unused()
  override val alters: StateFlow<APIState<List<MyAlter>>> get() = unused()
  override val frontHistory get() = unused()

  override fun deleteFront(frontID: String) = unused()
  override fun loadFrontHistory(monthYearPair: MonthYearPair) = unused()
}
