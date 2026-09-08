package app.interfold.app.ui.model.main.hometabs.alters.tagview

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.main.hometabs.alters.TagViewComponent

interface TagViewSettingsComponent : CommonInterface {
  val model: TagViewComponent.Model

  fun setParentTagID(parentTagID: String)
  fun removeParentTagID()
}

class TagViewSettingsComponentImpl(
  componentContext: MainComponentContext,
  override val model: TagViewComponent.Model
) : TagViewSettingsComponent, MainComponentContext by componentContext {
  override fun setParentTagID(parentTagID: String) {
    api.setParentTagID(model.id, parentTagID)
  }

  override fun removeParentTagID() {
    api.removeParentTagID(model.id)
  }
}