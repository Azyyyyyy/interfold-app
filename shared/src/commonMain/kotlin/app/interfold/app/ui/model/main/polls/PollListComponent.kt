package app.interfold.app.ui.model.main.polls

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext

interface PollListComponent : CommonInterface {
  fun navigateToPollView(pollID: String)
}

class PollListComponentImpl(
  componentContext: MainComponentContext,
  val navigateToPollViewFun: (String) -> Unit
) : PollListComponent, MainComponentContext by componentContext {
  override fun navigateToPollView(pollID: String) {
    navigateToPollViewFun(pollID)
  }
}