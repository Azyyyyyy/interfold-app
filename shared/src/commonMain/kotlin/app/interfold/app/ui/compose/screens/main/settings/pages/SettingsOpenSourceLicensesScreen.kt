package app.interfold.app.ui.compose.screens.main.settings.pages

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.interfold.app.ui.compose.LocalChildPanelsMode
import app.interfold.app.ui.compose.components.FixedLibrariesContainer
import app.interfold.app.ui.compose.components.shared.BackNavigationButton
import app.interfold.app.ui.compose.components.shared.InterScaffold
import app.interfold.app.ui.compose.components.shared.InterTopBar
import app.interfold.app.ui.compose.components.shared.TitleTextState
import app.interfold.app.ui.model.main.settings.SettingsOpenSourceLicensesComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.rememberLibraries
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.open_source_licenses

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun SettingsOpenSourceLicensesScreen(
  component: SettingsOpenSourceLicensesComponent
) {
  // val platformUtilities: PlatformUtilities = component.platformUtilities

  /*val updateTopBarNavigation = LocalUpdateTopBarNavigationComposable.current
  val updateTitleText = LocalUpdateTitleText.current

  LaunchedEffect(true) {
    updateTitleText(TitleTextState("Open source licenses"))
    updateTopBarNavigation {
      IconButton(onClick = navigator::pop) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Back"
        )
      }
    }
  }*/

  val libraries by rememberLibraries()

  InterScaffold(
    topBar = { topAppBarState, scrollBehavior, _ ->
      InterTopBar(
        navigation = {
          val childPanelsMode = LocalChildPanelsMode.current

          if(childPanelsMode == ChildPanelsMode.SINGLE) {
            BackNavigationButton(component::navigateBack)
          }
        },
        titleTextState = TitleTextState(Res.string.open_source_licenses.compose),
        topAppBarState = topAppBarState,
        scrollBehavior = scrollBehavior
      )
    },
    content = { _, _ ->
      FixedLibrariesContainer(
        libraries = libraries,
        /*licenseDialogBody = { library ->
            Text(library.licenses.filter { !it.licenseContent.isNullOrEmpty() }
              .joinToString(separator = "\n\n") { license -> license.licenseContent!! })
          },
          licenseDialogConfirmText = "Ok",*/
        modifier = Modifier.fillMaxSize()
      )
    }
  )
}
