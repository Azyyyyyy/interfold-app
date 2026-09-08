package app.interfold.app.ui.model.main.hometabs

import app.interfold.app.api.APIState
import kotlinx.datetime.Month
import app.interfold.app.api.model.MyAlter
import app.interfold.app.ui.compose.screens.main.hometabs.FrontHistoryItem
import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.utils.MonthYearPair
import kotlinx.coroutines.flow.StateFlow

interface FrontHistoryComponent {
  val settings: SettingsInterface

  val alters: StateFlow<APIState<List<MyAlter>>>
  val frontHistory: StateFlow<Map<MonthYearPair, APIState<List<Pair<Triple<Int, Month, Int>, MutableList<FrontHistoryItem>>>>>>

  fun deleteFront(frontID: String)
  fun loadFrontHistory(monthYearPair: MonthYearPair)
}

class FrontHistoryComponentImpl(
  componentContext: MainComponentContext
) : FrontHistoryComponent, MainComponentContext by componentContext {
  override val alters = api.alters
  override val frontHistory = api.frontHistory

  override fun deleteFront(frontID: String) {
    api.deleteFront(frontID)
  }

  override fun loadFrontHistory(monthYearPair: MonthYearPair) {
    api.loadFrontHistory(monthYearPair)
  }
}