package app.interfold.app.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.interfold.app.Settings
import app.interfold.app.api.model.AlterJournalEntry
import app.interfold.app.ui.compose.theme.ThemeFromColor
import app.interfold.app.utils.compose
import app.interfold.app.utils.dateTimeFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.locked
import interfoldapp.shared.resources.pinned

@Composable
fun LazyItemScope.AlterJournalEntryCard(
  journalEntry: AlterJournalEntry,
  launchViewJournalEntry: (AlterJournalEntry) -> Unit,
  launchOpenJournalEntrySheet: (AlterJournalEntry) -> Unit,
  settings: Settings
) {
  val haptics = LocalHapticFeedback.current

  ThemeFromColor(
    journalEntry.color,
    colorMode = settings.colorMode,
    dynamicColorType = settings.dynamicColorType,
    colorContrastLevel = settings.colorContrastLevel,
    amoledMode = settings.amoledMode,
    reduceMotion = settings.reduceMotion
  ) {
    Card(
      modifier = Modifier.fillMaxWidth().animateItem(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
      )
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .combinedClickable(
            onClick = { launchViewJournalEntry(journalEntry) },
            onLongClick = {
              launchOpenJournalEntrySheet(journalEntry)
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
          )
      ) {
        Row(
          modifier = Modifier.fillMaxSize().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              journalEntry.insertedAt.toLocalDateTime(TimeZone.currentSystemDefault())
                .dateTimeFormat(),
              style = MaterialTheme.typography.labelSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              journalEntry.title,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 4,
              overflow = TextOverflow.Ellipsis
            )
          }
          AnimatedVisibility(
            visible = journalEntry.locked || journalEntry.pinned,
            enter = fadeIn() + scaleIn(),
            exit = scaleOut() + fadeOut()
          ) {
            AnimatedContent(
              targetState = journalEntry.locked,
            ) {
              if (it) {
                Icon(
                  Icons.Rounded.Lock,
                  tint = MaterialTheme.colorScheme.tertiary,
                  contentDescription = Res.string.locked.compose
                )
              } else {
                Icon(
                  Icons.Rounded.PushPin,
                  tint = MaterialTheme.colorScheme.tertiary,
                  contentDescription = Res.string.pinned.compose
                )
              }
            }
          }
        }
      }
    }
  }
}