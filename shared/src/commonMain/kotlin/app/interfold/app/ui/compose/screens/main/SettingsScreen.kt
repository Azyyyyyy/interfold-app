package app.interfold.app.ui.compose.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.interfold.app.ui.compose.components.DoublePanels
import app.interfold.app.ui.compose.components.interfoldLogoVectorPainter
import app.interfold.app.ui.compose.screens.GLOBAL_PADDING
import app.interfold.app.ui.compose.screens.main.settings.SettingsRootScreen
import app.interfold.app.ui.compose.screens.main.settings.pages.SettingsAccessibilityScreen
import app.interfold.app.ui.compose.screens.main.settings.pages.SettingsAppearanceScreen
import app.interfold.app.ui.compose.screens.main.settings.pages.SettingsCustomFieldsScreen
import app.interfold.app.ui.compose.screens.main.settings.pages.SettingsOpenSourceLicensesScreen
import app.interfold.app.ui.compose.screens.main.settings.pages.SettingsSecurityScreen
import app.interfold.app.ui.model.main.settings.SettingsComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.derive
import com.arkivanov.decompose.ExperimentalDecomposeApi
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.app_logo
import interfoldapp.shared.resources.select_settings_placeholder

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun SettingsScreen(
  component: SettingsComponent
) {
  val settings by component.settings.collectAsState()
  val reduceMotion by derive { settings.reduceMotion }

  DoublePanels(
    panelsValue = component.panels,
    setMode = component::setMode,
    backHandler = component.backHandler,
    onBackPressed = component::onBackPressed,
    main = { SettingsRootScreen(it.instance) },
    details = {
      when(val child = it.instance) {
        is SettingsComponent.DetailsChild.SettingsAppearanceChild -> SettingsAppearanceScreen(child.component)
        is SettingsComponent.DetailsChild.SettingsAccessibilityChild -> SettingsAccessibilityScreen(child.component)
        is SettingsComponent.DetailsChild.SettingsSecurityChild -> SettingsSecurityScreen(child.component)
        is SettingsComponent.DetailsChild.SettingsCustomFieldsChild -> SettingsCustomFieldsScreen(child.component)
        is SettingsComponent.DetailsChild.SettingsOpenSourceLicensesChild -> SettingsOpenSourceLicensesScreen(child.component)
      }
    },
    reduceMotion = reduceMotion,
    placeholder = { SettingsPanelPlaceholder(reduceMotion) },
  )
}

@Composable
private fun SettingsPanelPlaceholder(reduceMotion: Boolean) {
  Surface(modifier = Modifier.fillMaxSize()) {
    Box(
      contentAlignment = Alignment.Center
    ) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(GLOBAL_PADDING),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Image(
            painter = interfoldLogoVectorPainter(animate = !reduceMotion),
            contentDescription = Res.string.app_logo.compose,
            modifier = Modifier.size(128.dp)
          )
          Spacer(Modifier.size(16.dp))
          Text(Res.string.select_settings_placeholder.compose)
        }
      }
    }
  }
}