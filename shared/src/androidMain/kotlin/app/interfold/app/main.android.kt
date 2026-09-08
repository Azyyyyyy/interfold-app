package app.interfold.app
import androidx.compose.runtime.Composable
import app.interfold.app.ui.compose.screens.RootScreen
import app.interfold.app.ui.model.RootComponent

/*
@Composable
fun AndroidAppWrapper(
  initialSettings: Settings,
  platformUtilities: PlatformUtilities,
  platformEventFlow: Flow<PlatformEvent>
) =
  App(initialSettings, platformUtilities, platformEventFlow)*/

@Composable
fun AndroidAppWrapper(rootComponent: RootComponent) = RootScreen(rootComponent)