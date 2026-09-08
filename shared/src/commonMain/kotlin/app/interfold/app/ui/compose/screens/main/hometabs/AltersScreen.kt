package app.interfold.app.ui.compose.screens.main.hometabs

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
import app.interfold.app.Settings
import app.interfold.app.ui.compose.components.TriplePanels
import app.interfold.app.ui.compose.components.interfoldLogoVectorPainter
import app.interfold.app.ui.compose.screens.GLOBAL_PADDING
import app.interfold.app.ui.compose.screens.main.hometabs.alters.AlterJournalEntryViewScreen
import app.interfold.app.ui.compose.screens.main.hometabs.alters.AlterListScreen
import app.interfold.app.ui.compose.screens.main.hometabs.alters.AlterViewScreen
import app.interfold.app.ui.compose.screens.main.hometabs.alters.TagViewScreen
import app.interfold.app.ui.model.main.hometabs.AltersComponent
import app.interfold.app.ui.model.main.hometabs.AltersDetailStackComponent
import app.interfold.app.utils.backAnimation
import app.interfold.app.utils.compose
import app.interfold.app.utils.derive
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.experimental.stack.ChildStack
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.app_logo
import interfoldapp.shared.resources.select_alter_or_tag_placeholder

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun AltersScreen(
  component: AltersComponent
) {
  val settings by component.settings.collectAsState()
  val reduceMotion by derive { settings.reduceMotion }

  TriplePanels(
    panelsValue = component.panels,
    setMode = component::setMode,
    backHandler = component.backHandler,
    onBackPressed = component::onBackPressed,
    main = { AlterListScreen(it.instance) },
    details = {
      AltersDetailStackScreen(it.instance)
    },
    extra = {
      when (val child = it.instance) {
        is AltersComponent.ExtraChild.AlterJournalEntryChild -> AlterJournalEntryViewScreen(child.component)
      }
    },
    placeholder = { AlterPanelPlaceholder(reduceMotion) },
    reduceMotion = reduceMotion
  )
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun AltersDetailStackScreen(
  component: AltersDetailStackComponent
) {
  val settings: Settings by component.settings.collectAsState()
  val screenTransitionType by derive { settings.screenTransitionType }
  val reduceMotion by derive { settings.reduceMotion }

  ChildStack(
    component.stack,
    animation = backAnimation(screenTransitionType, reduceMotion, component.backHandler, component::onBackPressed),
  ) {
    when (val child = it.instance) {
      is AltersDetailStackComponent.Child.AlterViewChild -> AlterViewScreen(child.component)
      is AltersDetailStackComponent.Child.TagViewChild -> TagViewScreen(child.component)
    }
  }
}

@Composable
private fun AlterPanelPlaceholder(reduceMotion: Boolean) {
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
          Text(Res.string.select_alter_or_tag_placeholder.compose)
        }
      }
    }
  }
}