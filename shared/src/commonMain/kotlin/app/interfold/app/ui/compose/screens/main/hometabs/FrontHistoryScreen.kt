package app.interfold.app.ui.compose.screens.main.hometabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import app.interfold.app.ui.compose.LocalNavigationType
import app.interfold.app.ui.compose.LocalUpdateLazyListState
import app.interfold.app.ui.compose.NavigationType
import app.interfold.app.ui.compose.components.FrontHistoryItemCard
import app.interfold.app.ui.compose.components.shared.BottomSheetListItem
import app.interfold.app.ui.compose.components.shared.IndeterminateProgressSpinner
import app.interfold.app.ui.compose.components.shared.InterBottomSheet
import app.interfold.app.ui.compose.components.shared.InterScaffold
import app.interfold.app.ui.compose.components.shared.InterTopBar
import app.interfold.app.ui.compose.components.shared.OpenDrawerNavigationButton
import app.interfold.app.ui.compose.components.shared.PermanentTipsNote
import app.interfold.app.ui.compose.components.shared.TitleTextState
import app.interfold.app.ui.compose.screens.GLOBAL_PADDING
import app.interfold.app.ui.compose.theme.squareifyShape
import app.interfold.app.ui.compose.utils.SpotlightTooltip
import app.interfold.app.ui.model.main.hometabs.FrontHistoryComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.currentMonthYearPair
import app.interfold.app.utils.dateFormat
import app.interfold.app.utils.derive
import app.interfold.app.utils.description
import app.interfold.app.utils.ioDispatcher
import app.interfold.app.utils.nextMonth
import app.interfold.app.utils.platformLog
import app.interfold.app.utils.previousMonth
import app.interfold.app.utils.state
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.collapse
import interfoldapp.shared.resources.delete_front
import interfoldapp.shared.resources.expand
import interfoldapp.shared.resources.history
import interfoldapp.shared.resources.next_month
import interfoldapp.shared.resources.permanent_tip_front_history
import interfoldapp.shared.resources.previous_month
import interfoldapp.shared.resources.tooltip_delete_front_desc
import interfoldapp.shared.resources.tooltip_history_desc
import org.jetbrains.compose.resources.stringResource

enum class FrontHistoryTimeType {
  PARTIAL,
  ALL_DAY,
  INFINITIVE_START,
  INFINITIVE_END,
}

data class FrontHistoryItem(
  val frontID: String,
  val alterID: Int,
  val comment: String?,
  val timeStarted: LocalDateTime,
  val timeEnded: LocalDateTime,
  val type: FrontHistoryTimeType
)

@Composable
fun FrontHistoryScreen(
  component: FrontHistoryComponent
) {
  val settings by component.settings.collectAsState()
  val alters by component.alters.collectAsState()

  val showPermanentTips by derive {
    settings.showPermanentTips
  }

  val currentMonth = state(currentMonthYearPair())

  val frontHistoryMap by component.frontHistory.collectAsState()
  val clusteredFrontHistory: List<Pair<Triple<Int, Month, Int>, List<FrontHistoryItem>>> by derive {
    if (
      frontHistoryMap[currentMonth.value] == null
      || !frontHistoryMap[currentMonth.value]!!.isSuccess
    ) {
      emptyList()
    } else {
      frontHistoryMap[currentMonth.value]!!.ensureData
    }
  }

  val ready by derive {
    alters.isSuccess && (frontHistoryMap[currentMonth.value]?.isSuccess == true)
  }

  val collapsedStates = remember(clusteredFrontHistory) {
    mutableStateMapOf(
      *clusteredFrontHistory.map { it.first }.map { it to false }.toTypedArray()
    )
  }

  // val updateTopBarActions = LocalUpdateTopBarActionsComposable.current

  /*LaunchedEffect(collapsedStates) {
    updateTopBarActions {
      if (!alters.isSuccess) return@updateTopBarActions
      var expanded by state(false)
      TopBarActions(
        toggleExpanded = { expanded = it },
        isExpanded = expanded,
        expandAll = {
          if (!frontHistoryMap.contains(currentMonth.value) || !frontHistoryMap[currentMonth.value]!!.isSuccess)
            return@TopBarActions
          collapsedStates.keys.forEach { collapsedStates[it] = false }
        },
        collapseAll = {
          if (!frontHistoryMap.contains(currentMonth.value) || !frontHistoryMap[currentMonth.value]!!.isSuccess)
            return@TopBarActions
          collapsedStates.keys.forEach { collapsedStates[it] = true }
        }
      )
    }

    if (frontHistoryMap.contains(currentMonth.value)) return@LaunchedEffect

    apiViewModel.loadFrontHistory(currentMonth.value)
  }*/

  LaunchedEffect(currentMonth.value) {
    if (frontHistoryMap.contains(currentMonth.value)) return@LaunchedEffect

    component.loadFrontHistory(currentMonth.value)
  }

  var firstItemNotVisible by state(false)

  val imageScope = rememberCoroutineScope()
  val placeholderPainter = rememberVectorPainter(Icons.Rounded.Person)

  val lazyListState = rememberLazyListState()

  var selectedFrontItem by state<FrontHistoryItem?>()

  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.firstVisibleItemIndex }
      .distinctUntilChanged()
      .collect {
        firstItemNotVisible = it > 0
      }
  }

  val updateLazyListState = LocalUpdateLazyListState.current

  LaunchedEffect(true) {
    updateLazyListState(lazyListState)
  }

  InterScaffold(
    hasHoistedBottomBar = true,
    topBar = { topAppBarState, scrollBehavior, _ ->
      InterTopBar(
        titleTextState = TitleTextState(
          title = Res.string.history.compose,
          spotlightText = Res.string.history.compose to Res.string.tooltip_history_desc.compose
        ),
        navigation = {
          val navigationType = LocalNavigationType.current

          if(navigationType == NavigationType.BOTTOM_BAR) {
            OpenDrawerNavigationButton()
          }
        },
        actions = {
          if (alters.isSuccess) {
            var expanded by state(false)
            TopBarActions(
              toggleExpanded = { expanded = it },
              isExpanded = expanded,
              expandAll = {
                if (!frontHistoryMap.contains(currentMonth.value) || !frontHistoryMap[currentMonth.value]!!.isSuccess)
                  return@TopBarActions
                collapsedStates.keys.forEach { collapsedStates[it] = false }
              },
              collapseAll = {
                if (!frontHistoryMap.contains(currentMonth.value) || !frontHistoryMap[currentMonth.value]!!.isSuccess)
                  return@TopBarActions
                collapsedStates.keys.forEach { collapsedStates[it] = true }
              }
            )
          }
        },
        topAppBarState = topAppBarState,
        scrollBehavior = scrollBehavior
      )
    },
    content = { _, _ ->
      LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize()
      ) {
        item {
          Column(
            modifier = Modifier.padding(horizontal = GLOBAL_PADDING),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(top = GLOBAL_PADDING),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              FilledIconButton(onClick = {
                currentMonth.value = currentMonth.value.previousMonth()
              }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                  contentDescription = Res.string.previous_month.compose
                )
              }
              Text(
                currentMonth.value.description,
                style = MaterialTheme.typography.titleMedium
              )
              FilledIconButton(onClick = {
                currentMonth.value = currentMonth.value.nextMonth()
              }) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                  contentDescription = Res.string.next_month.compose
                )
              }
            }
          }
        }

        if (showPermanentTips) {
          item {
            PermanentTipsNote(
              text = Res.string.permanent_tip_front_history.compose,
              modifier = Modifier.padding(horizontal = GLOBAL_PADDING, vertical = 8.dp)
            )
          }
        }

        if (!ready) {
          item {
            IndeterminateProgressSpinner()
          }
        } else {
          if (clusteredFrontHistory.isEmpty()) {
            item {
              Row(
                modifier = Modifier.padding(GLOBAL_PADDING).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
              ) {
                Text(
                  "No front history for this month.",
                  style = MaterialTheme.typography.bodyMedium
                )
              }
            }
          } else {
            clusteredFrontHistory.forEach {
              val triple = it.first
              val frontItems = it.second

              stickyHeader(key = triple) {
                val collapsed = collapsedStates[triple] == true

                val stuck by derive {
                  lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.key == triple
                }

                val cornerRadius by animateDpAsState(
                  if (stuck) 24.dp else 0.dp
                )

                val surfaceElevation by animateDpAsState(
                  if (stuck) 3.dp else 0.dp
                )

                val containerColor by animateColorAsState(
                  if (stuck) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface
                )

                Surface(
                  modifier = Modifier.fillMaxWidth(),
                  color = containerColor,
                  shape = squareifyShape(settings.cornerStyle) {
                    RoundedCornerShape(
                      bottomStart = cornerRadius,
                      bottomEnd = cornerRadius,
                    )
                  },
                  shadowElevation = surfaceElevation
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 8.dp).clickable {
                      collapsedStates[triple] = !collapsed
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = GLOBAL_PADDING),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        LocalDateTime(triple.third, triple.second, triple.first, 0, 0)
                          .dateFormat(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                      )
                      Icon(
                        imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                        contentDescription = stringResource(
                          if (collapsed) Res.string.expand
                          else Res.string.collapse
                        )
                      )
                    }
                  }
                }
              }

              if (collapsedStates[triple] == false) {
                items(
                  frontItems,
                  key = { frontItem -> triple.toString() + frontItem.frontID }
                ) { frontItem ->
                  val alter = remember(frontItem) { alters.ensureData.find { alter -> alter.id == frontItem.alterID } }
                  if(alter != null) {
                    FrontHistoryItemCard(
                      frontHistoryItem = frontItem,
                      alter = alter,
                      onClick = {
                        selectedFrontItem = frontItem
                      },
                      imageContext = imageScope.coroutineContext + ioDispatcher,
                      placeholderPainter = placeholderPainter,
                      settings = settings
                    )
                  } else {
                    platformLog(frontItem.toString())
                  }
                }
              }
            }

          }
        }

        item {
          Spacer(modifier = Modifier.size(16.dp))
        }
      }

      if (selectedFrontItem != null) {
        FrontHistoryItemContextSheet(
          onDismissRequest = { selectedFrontItem = null },
          launchDeleteFront = {
            component.deleteFront(selectedFrontItem!!.frontID)
          }
        )
      }
    },
  )
}


@Composable
private fun FrontHistoryItemContextSheet(
  onDismissRequest: () -> Unit,
  launchDeleteFront: () -> Unit,
) {
  InterBottomSheet(
    onDismissRequest = onDismissRequest
  ) {
    SpotlightTooltip(
      title = Res.string.delete_front.compose,
      description = Res.string.tooltip_delete_front_desc.compose
    ) {
      BottomSheetListItem(
        imageVector = Icons.Rounded.Delete,
        iconTint = MaterialTheme.colorScheme.error,
        title = Res.string.delete_front.compose
      ) {
        launchDeleteFront()
        onDismissRequest()
      }
    }
  }
}

@Composable
private fun RowScope.TopBarActions(
  toggleExpanded: (Boolean) -> Unit,
  isExpanded: Boolean,
  expandAll: () -> Unit,
  collapseAll: () -> Unit
) {
  IconButton(onClick = {
    toggleExpanded(!isExpanded)
  }) {
    Icon(
      imageVector = Icons.Rounded.MoreVert,
      contentDescription = null
    )
  }
  DropdownMenu(
    expanded = isExpanded,
    onDismissRequest = { toggleExpanded(false) },
    properties = PopupProperties()
  ) {
    DropdownMenuItem(
      text = { Text("Collapse all") },
      onClick = {
        collapseAll()
        toggleExpanded(false)
      },
      leadingIcon = {
        Icon(
          Icons.Rounded.ExpandLess,
          contentDescription = null
        )
      }
    )
    DropdownMenuItem(
      text = { Text("Expand all") },
      onClick = {
        expandAll()
        toggleExpanded(false)
      },
      leadingIcon = {
        Icon(
          Icons.Rounded.ExpandMore,
          contentDescription = null
        )
      })
  }
}