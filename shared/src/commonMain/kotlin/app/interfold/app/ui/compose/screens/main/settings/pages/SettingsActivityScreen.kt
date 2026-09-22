package app.interfold.app.ui.compose.screens.main.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.interfold.app.telemetry.ActivityEvent
import app.interfold.app.telemetry.ActivityFilter
import app.interfold.app.telemetry.ActivityStatus
import app.interfold.app.telemetry.OtlpDiscoveryState
import app.interfold.app.telemetry.filtered
import app.interfold.app.ui.compose.LocalChildPanelsMode
import app.interfold.app.ui.compose.components.SettingsSection
import app.interfold.app.ui.compose.components.SettingsToggleItem
import app.interfold.app.ui.compose.components.shared.BackNavigationButton
import app.interfold.app.ui.compose.components.shared.InterScaffold
import app.interfold.app.ui.compose.components.shared.InterTopBar
import app.interfold.app.ui.compose.components.shared.TitleTextState
import app.interfold.app.ui.compose.screens.GLOBAL_PADDING
import app.interfold.app.ui.model.main.settings.SettingsActivityComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.currentPlatform
import app.interfold.app.utils.setText
import app.interfold.app.utils.state
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.activity
import interfoldapp.shared.resources.activity_clear
import interfoldapp.shared.resources.activity_copy
import interfoldapp.shared.resources.activity_custom_otlp
import interfoldapp.shared.resources.activity_empty
import interfoldapp.shared.resources.activity_export_unavailable_web
import interfoldapp.shared.resources.activity_filter_all
import interfoldapp.shared.resources.activity_filter_errors
import interfoldapp.shared.resources.activity_filter_logs
import interfoldapp.shared.resources.activity_filter_network
import interfoldapp.shared.resources.activity_server_no_otlp
import interfoldapp.shared.resources.activity_server_otlp
import interfoldapp.shared.resources.activity_share_with_server
import interfoldapp.shared.resources.tooltip_activity_custom_otlp_desc
import interfoldapp.shared.resources.tooltip_activity_share_with_server_desc
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun SettingsActivityScreen(
  component: SettingsActivityComponent
) {
  val settings by component.settings.collectAsState()
  val events by component.events.collectAsState()
  val discovery by component.discovery.collectAsState()
  var filter by state(ActivityFilter.NETWORK)
  val clipboard = LocalClipboard.current
  val exportSupported = !currentPlatform.isWasm

  LaunchedEffect(exportSupported, settings.shareActivityWithServer, settings.apiEndpoint) {
    if (exportSupported && settings.shareActivityWithServer) {
      component.refreshDiscovery()
    }
  }

  val visible = events.asReversed().filtered(filter)

  InterScaffold(
    topBar = { topAppBarState, scrollBehavior, _ ->
      InterTopBar(
        navigation = {
          val childPanelsMode = LocalChildPanelsMode.current
          if (childPanelsMode == ChildPanelsMode.SINGLE) {
            BackNavigationButton(component::navigateBack)
          }
        },
        titleTextState = TitleTextState(Res.string.activity.compose),
        topAppBarState = topAppBarState,
        scrollBehavior = scrollBehavior
      )
    },
    content = { _, _ ->
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GLOBAL_PADDING)
      ) {
        if (exportSupported) {
          SettingsSection(
            null,
            settings,
            {
              SettingsToggleItem(
                text = Res.string.activity_share_with_server.compose,
                value = settings.shareActivityWithServer,
                spotlightDescription = Res.string.tooltip_activity_share_with_server_desc.compose,
                cardGroupPosition = it,
                updateValue = component::setShareActivityWithServer,
              )
            },
          )
        } else {
          item { Spacer(modifier = Modifier.height(GLOBAL_PADDING)) }
        }
        item {
          val statusText = when {
            !exportSupported -> Res.string.activity_export_unavailable_web.compose
            !settings.shareActivityWithServer -> ""
            discovery is OtlpDiscoveryState.Available ->
              stringResource(
                Res.string.activity_server_otlp,
                (discovery as OtlpDiscoveryState.Available).url,
              )
            discovery is OtlpDiscoveryState.Checking -> ""
            else -> Res.string.activity_server_no_otlp.compose
          }
          if (statusText.isNotEmpty()) {
            Text(
              text = statusText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 8.dp),
            )
          }
        }
        if (exportSupported) {
          item {
            var customUrl by state(settings.otlpEndpoint)
            OutlinedTextField(
              value = customUrl,
              onValueChange = {
                customUrl = it
                component.setOtlpEndpoint(it)
              },
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              label = { Text(Res.string.activity_custom_otlp.compose) },
              supportingText = { Text(Res.string.tooltip_activity_custom_otlp_desc.compose) },
              placeholder = { Text("http://host:4318") },
            )
          }
        }
        item {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
          ) {
            ActivityFilter.entries.forEach { option ->
              FilterChip(
                selected = filter == option,
                onClick = { filter = option },
                label = {
                  Text(
                    when (option) {
                      ActivityFilter.ALL -> Res.string.activity_filter_all.compose
                      ActivityFilter.NETWORK -> Res.string.activity_filter_network.compose
                      ActivityFilter.PLATFORM_LOG -> Res.string.activity_filter_logs.compose
                      ActivityFilter.ERRORS -> Res.string.activity_filter_errors.compose
                    }
                  )
                },
              )
            }
          }
        }
        item {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = component::clearActivity) {
              Text(Res.string.activity_clear.compose)
            }
            TextButton(
              onClick = {
                clipboard.nativeClipboard.setText(AnnotatedString(visible.toCopyText()))
              }
            ) {
              Text(Res.string.activity_copy.compose)
            }
          }
        }
        if (visible.isEmpty()) {
          item {
            Text(
              Res.string.activity_empty.compose,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(vertical = 16.dp),
            )
          }
        } else {
          items(visible, key = { it.id }) { event ->
            ActivityEventRow(event)
          }
        }
        item { Spacer(modifier = Modifier.height(GLOBAL_PADDING)) }
      }
    }
  )
}

@Composable
private fun ActivityEventRow(event: ActivityEvent) {
  val color = if (event.status == ActivityStatus.ERROR) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(event.name, style = MaterialTheme.typography.labelLarge, color = color)
    Text(
      "${formatMillis(event.startedAtMillis)} · ${event.durationMillis}ms · ${event.scope.name}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    event.message?.let {
      Text(it, style = MaterialTheme.typography.bodySmall)
    }
    event.attributes.forEach { (key, value) ->
      if (key == "log.message") return@forEach
      if (key == "exception.message" && value == event.message) return@forEach
      Text("$key=$value", style = MaterialTheme.typography.bodySmall)
    }
  }
}

private fun formatMillis(millis: Long): String {
  val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
  val minute = local.minute.toString().padStart(2, '0')
  val second = local.second.toString().padStart(2, '0')
  return "${local.hour}:$minute:$second"
}

private fun List<ActivityEvent>.toCopyText(): String =
  joinToString("\n") { event ->
    buildString {
      append(formatMillis(event.startedAtMillis))
      append(" ")
      append(event.name)
      append(" [")
      append(event.status)
      append("] ")
      append(event.durationMillis)
      append("ms")
      event.message?.let { append(" ").append(it) }
    }
  }
