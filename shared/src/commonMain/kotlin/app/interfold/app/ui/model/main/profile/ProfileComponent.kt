package app.interfold.app.ui.model.main.profile

import app.interfold.app.api.APIState
import app.interfold.app.api.model.APIError
import app.interfold.app.api.model.MyAlter
import app.interfold.app.api.model.MySystem
import app.interfold.app.ui.model.MainComponentContext
import app.interfold.app.ui.model.interfaces.ImageCropper
import app.interfold.app.ui.model.interfaces.SettingsInterface
import app.interfold.app.utils.DevicePlatform
import app.interfold.app.utils.PlatformUtilities
import com.arkivanov.essenty.instancekeeper.retainedInstance
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.mr0xf00.easycrop.core.crop.forceSquareCropState
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProfileComponent {
  val errorFlow: Flow<APIError>
  val system: StateFlow<APIState<MySystem>>
  val alters: StateFlow<APIState<List<MyAlter>>>
  val platformUtilities: PlatformUtilities
  val settings: SettingsInterface

  val imageCropper: ImageCropper?

  fun updateDescription(description: String?)
  fun updateUsername(username: String)
  fun setSystemAvatar(bytes: ByteArray, fileName: String)
  fun removeSystemAvatar()
}

class ProfileComponentImpl(
  componentContext: MainComponentContext
) : ProfileComponent, MainComponentContext by componentContext {
  val coroutineScope = coroutineScope(coroutineContext + SupervisorJob())
  override val imageCropper = if(!DevicePlatform.usesNativeImageCropper) {
    retainedInstance {
      ImageCropper(cropStateGenerator = ::forceSquareCropState, coroutineScope = coroutineScope)
    }
  } else null

  override val errorFlow = api.errorFlow
  override val system = api.systemMe
  override val alters = api.alters

  override fun updateUsername(username: String) = api.updateUsername(username)
  override fun updateDescription(description: String?) = api.updateDescription(description)
  override fun setSystemAvatar(bytes: ByteArray, fileName: String) = api.setSystemAvatar(bytes, fileName)
  override fun removeSystemAvatar() = api.removeSystemAvatar()
}