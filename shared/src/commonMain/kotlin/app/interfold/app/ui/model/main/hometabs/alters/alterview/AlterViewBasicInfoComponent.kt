package app.interfold.app.ui.model.main.hometabs.alters.alterview

import app.interfold.app.ui.model.CommonInterface
import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.ImageCropper
import app.interfold.app.ui.model.main.hometabs.alters.AlterViewComponent
import app.interfold.app.utils.DevicePlatform
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.mr0xf00.easycrop.core.crop.forceSquareCropState
import kotlinx.coroutines.SupervisorJob

interface AlterViewBasicInfoComponent : CommonInterface {
  val model: AlterViewComponent.Model

  val imageCropper: ImageCropper?

  fun navigateToTagView(tagID: String)
}

class AlterViewBasicInfoComponentImpl(
  componentContext: MainComponentContext,
  override val model: AlterViewComponent.Model,
  val navigateToTagViewFun: (String) -> Unit
) : AlterViewBasicInfoComponent, MainComponentContext by componentContext {
  val coroutineScope = coroutineScope(coroutineContext + SupervisorJob())
  override val imageCropper = if(!DevicePlatform.usesNativeImageCropper) {
    retainedInstance {
      ImageCropper(cropStateGenerator = ::forceSquareCropState, coroutineScope = coroutineScope)
    }
  } else null

  override fun navigateToTagView(tagID: String) {
    navigateToTagViewFun(tagID)
  }
}