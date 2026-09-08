package app.interfold.app.ui.model.main.hometabs.alters.alterview

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.main.hometabs.alters.AlterViewComponent

interface AlterViewFieldsComponent : CommonInterface {
  val model: AlterViewComponent.Model

  fun navigateToCustomFields()
}

class AlterViewFieldsComponentImpl(
  componentContext: MainComponentContext,
  override val model: AlterViewComponent.Model,
  private val navigateToCustomFieldsFun: () -> Unit
) : AlterViewFieldsComponent, MainComponentContext by componentContext {
  override fun navigateToCustomFields() = navigateToCustomFieldsFun()
}