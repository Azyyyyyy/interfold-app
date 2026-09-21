package app.interfold.app.ui.compose.screens.main.hometabs

import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.ui.model.main.hometabs.AltersComponent
import app.interfold.app.ui.model.main.hometabs.AltersDetailStackComponent
import app.interfold.app.ui.model.main.hometabs.FriendsComponent
import app.interfold.app.ui.model.main.hometabs.JournalComponent
import app.interfold.app.ui.model.main.hometabs.alters.AlterListComponent
import app.interfold.app.ui.model.main.hometabs.friends.FriendListComponent
import app.interfold.app.ui.model.main.hometabs.friends.FriendViewComponent
import app.interfold.app.ui.model.main.hometabs.journal.JournalEntryListComponent
import app.interfold.app.ui.model.main.hometabs.journal.JournalEntryViewComponent
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanels
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackDispatcher
import com.arkivanov.essenty.backhandler.BackHandler

private val stubBackHandler: BackHandler = BackDispatcher()

private fun unused(): Nothing =
  error("Inner tab component is not used in scaffold-only tests")

@OptIn(ExperimentalDecomposeApi::class)
private object StubAltersComponent : AltersComponent {
  override val settings: SettingsInterface get() = unused()
  override val panels: Value<ChildPanels<*, AlterListComponent, *, AltersDetailStackComponent, *, AltersComponent.ExtraChild>>
    get() = unused()
  override val backHandler: BackHandler = stubBackHandler
  override fun replaceWithAlterView(alterID: Int) = unused()
  override fun replaceWithTagView(tagID: String) = unused()
  override fun activateAlterJournalEntry(alterID: Int, entryID: String, alterColor: String?) = unused()
  override fun onBackPressed() = unused()
  override fun setMode(mode: ChildPanelsMode) = unused()
}

@OptIn(ExperimentalDecomposeApi::class)
private object StubFriendsComponent : FriendsComponent {
  override val settings: SettingsInterface get() = unused()
  override val panels: Value<ChildPanels<*, FriendListComponent, *, FriendViewComponent, *, FriendsComponent.ExtraChild>>
    get() = unused()
  override val backHandler: BackHandler = stubBackHandler
  override fun navigateToFriendView(friendID: String) = unused()
  override fun navigateToFriendTagView(friendID: String, tagID: String) = unused()
  override fun navigateToFriendAlterView(friendID: String, alterID: Int) = unused()
  override fun onBackPressed() = unused()
  override fun setMode(mode: ChildPanelsMode) = unused()
}

@OptIn(ExperimentalDecomposeApi::class)
private object StubJournalComponent : JournalComponent {
  override val settings: SettingsInterface get() = unused()
  override val panels: Value<ChildPanels<*, JournalEntryListComponent, *, JournalEntryViewComponent, Nothing, Nothing>>
    get() = unused()
  override val backHandler: BackHandler = stubBackHandler
  override fun navigateToJournalEntryView(entryID: String) = unused()
  override fun onBackPressed() = unused()
  override fun setMode(mode: ChildPanelsMode) = unused()
}

internal actual fun stubAltersComponent(): AltersComponent = StubAltersComponent
internal actual fun stubFriendsComponent(): FriendsComponent = StubFriendsComponent
internal actual fun stubJournalComponent(): JournalComponent = StubJournalComponent
