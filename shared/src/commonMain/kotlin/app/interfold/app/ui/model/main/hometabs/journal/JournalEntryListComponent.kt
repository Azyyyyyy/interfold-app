package app.interfold.app.ui.model.main.hometabs.journal

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface JournalEntryListComponent : CommonInterface {
  fun navigateToJournalEntryView(entryID: String)
}

class JournalEntryListComponentImpl(
  componentContext: MainComponentContext,
  private val navigateToJournalEntryViewFun: (String) -> Unit
) : JournalEntryListComponent, MainComponentContext by componentContext {
  override fun navigateToJournalEntryView(entryID: String) {
    navigateToJournalEntryViewFun(entryID)
  }
}