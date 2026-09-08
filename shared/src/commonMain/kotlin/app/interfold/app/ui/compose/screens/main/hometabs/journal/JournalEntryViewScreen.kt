@file:JvmName("JournalEntryViewScreenKt")

package app.interfold.app.ui.compose.screens.main.hometabs.journal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import app.interfold.app.api.model.MyAlter
import app.interfold.app.ui.compose.LocalChildPanelsMode
import app.interfold.app.ui.compose.components.DeleteJournalEntryDialog
import app.interfold.app.ui.compose.components.EditJournalTitleDialog
import app.interfold.app.ui.compose.components.GlobalJournalCardAddAlterButton
import app.interfold.app.ui.compose.components.GlobalJournalCardPlaceholderImage
import app.interfold.app.ui.compose.components.UnencryptedWarningDialog
import app.interfold.app.ui.compose.components.shared.AttachAlterDialog
import app.interfold.app.ui.compose.components.shared.BackNavigationButton
import app.interfold.app.ui.compose.components.shared.BottomSheetListItem
import app.interfold.app.ui.compose.components.shared.IndeterminateProgressSpinner
import app.interfold.app.ui.compose.components.shared.InterBottomSheet
import app.interfold.app.ui.compose.components.shared.InterLargeTopBar
import app.interfold.app.ui.compose.components.shared.InterScaffold
import app.interfold.app.ui.compose.components.shared.TitleTextState
import app.interfold.app.ui.compose.components.shared.UpdateColorDialog
import app.interfold.app.ui.compose.theme.ThemeFromColor
import app.interfold.app.ui.compose.utils.JournalEntryMarkdownTextField
import app.interfold.app.ui.compose.utils.SpotlightTooltip
import app.interfold.app.ui.model.main.SaveState
import app.interfold.app.ui.model.main.hometabs.journal.JournalEntryViewComponent
import app.interfold.app.utils.compose
import app.interfold.app.utils.derive
import app.interfold.app.utils.effectsSpec
import app.interfold.app.utils.localeFormatNumber
import app.interfold.app.utils.savedState
import app.interfold.app.utils.state
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.router.panels.ChildPanelsMode
import com.mikepenz.markdown.m3.markdownColor
import io.kamel.core.utils.cacheControl
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import kotlinx.coroutines.delay
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.confirm
import interfoldapp.shared.resources.delete_journal_entry
import interfoldapp.shared.resources.name_avatar
import interfoldapp.shared.resources.revert_changes
import interfoldapp.shared.resources.saving
import interfoldapp.shared.resources.tooltip_revert_changes_desc
import interfoldapp.shared.resources.unnamed_alter
import org.jetbrains.compose.resources.stringResource
import kotlin.jvm.JvmName

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun JournalEntryViewScreen(
  component: JournalEntryViewComponent
) {
  val api = component.api
  val settings by component.settings.collectAsState()
  val model = component.model

  /*if (!model.isLoaded.value) {
    IndeterminateProgressSpinner()
    return
  }*/

  val allAlters by api.alters.collectAsState()

  val title by model.title.collectAsState()
  val color by model.color.collectAsState()
  val contentState by model.contentState.collectAsState()
  val showUnencryptedWarning by model.showUnencryptedWarning.collectAsState()

  val alters by model.alters.collectAsState()

  val validAlters by derive {
    alters.mapNotNull { alterId ->
      allAlters.ensureData.find { it.id == alterId }
    }
  }

  // val coroutineScope = rememberCoroutineScope()

  val imageScope = rememberCoroutineScope()
  val placeholderPainter = rememberVectorPainter(Icons.Rounded.Person)
  val addPainter = rememberVectorPainter(Icons.Rounded.Add)

  var deleteEntryDialogOpen by savedState(false)
  var colorDialogOpen by savedState(false)
  var editTitleDialogOpen by savedState(false)
  var attachAlterDialogOpen by savedState(false)
  var alterToEdit by savedState<MyAlter?>(null)

  // val contentTextState = rememberTextFieldState()
  var isEditing by state(false)
  val isLoaded by model.isLoaded.collectAsState()
  val saveState by model.saveState.collectAsState()

  val initialEntry by model.initialEntry.collectAsState()

  val saving = Res.string.saving.compose

  val focusRequester = remember { FocusRequester() }
  val interactionSource = remember { MutableInteractionSource() }

  LaunchedEffect(isLoaded) {
    if(isLoaded) {
      isEditing = contentState.ensureContent.isNullOrBlank()
    }
  }

  /*LaunchedEffect(isLoaded) {
    if (isLoaded) {
      isEditing = contentState.ensureContent.isNullOrBlank()
      contentTextState.edit {
        replace(0, length, contentState.ensureContent.orEmpty())
      }
    }
  }*/

  LaunchedEffect(isEditing) {
    if(isEditing) {
      delay(100)
      focusRequester.requestFocus()
    }
  }

  val titleTextState by derive {
    if(saveState == SaveState.Saving) {
      TitleTextState(title = saving, oneLine = false)
    } else {
      TitleTextState(title = title, onClick = {
        editTitleDialogOpen = true
      }, oneLine = false)
    }
  }

  ThemeFromColor(
    color,
    colorMode = settings.colorMode,
    dynamicColorType = settings.dynamicColorType,
    colorContrastLevel = settings.colorContrastLevel,
    amoledMode = settings.amoledMode,
    reduceMotion = settings.reduceMotion
  ) {
    val markdownColors = markdownColor()

    InterScaffold(
      hasHoistedBottomBar = true,
      topBar = { topAppBarState, scrollBehavior, _ ->
        InterLargeTopBar(
          navigation = {
            val childPanelsMode = LocalChildPanelsMode.current

            if(childPanelsMode == ChildPanelsMode.SINGLE) {
              BackNavigationButton(component::navigateBack)
            }
          },
          actions = {
            TopBarActions(
              ready = isLoaded,
              revertChanges = model::revertChanges,
              launchChangeColor = { colorDialogOpen = true },
              launchDeleteEntry = { deleteEntryDialogOpen = true }
            )
          },
          titleTextState = titleTextState,
          topAppBarState = topAppBarState,
          scrollBehavior = scrollBehavior
        )
      },
      floatingActionButton = {
        AnimatedVisibility(
          visible = isEditing,
          enter = fadeIn(effectsSpec()),
          exit = fadeOut(effectsSpec())
        ) {
          FloatingActionButton(
            onClick = { isEditing = false },
          ) {
            Icon(
              imageVector = Icons.Rounded.Done,
              contentDescription = Res.string.confirm.compose
            )
          }
        }
      }
    ) {  padding, showSnackbar ->
      LaunchedEffect(showSnackbar) {
        component.updateShowSnackbar(showSnackbar)
      }
      Surface(
        modifier = Modifier.fillMaxSize().clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null
        ) {
          try {
            focusRequester.requestFocus()
          } catch (_: Exception) {
          }
        },
      ) {
        if (!isLoaded) {
          IndeterminateProgressSpinner()
          return@Surface
        }

        JournalEntryMarkdownTextField(
          isEditing = isEditing,
          updateIsEditing = { isEditing = it },
          contentState = contentState,
          // contentTextState = contentTextState,
          updateContent = model::updateContent,
          padding = padding,
          focusRequester = focusRequester,
          interactionSource = interactionSource,
          markdownColors = markdownColors
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
              ) {
                if (validAlters.isNotEmpty()) {
                  validAlters.forEach { alter ->
                    ThemeFromColor(
                      alter.color,
                      colorMode = settings.colorMode,
                      dynamicColorType = settings.dynamicColorType,
                      colorContrastLevel = settings.colorContrastLevel,
                      amoledMode = settings.amoledMode,
                      reduceMotion = settings.reduceMotion
                    ) {
                      if (alter.avatarUrl.isNullOrBlank()) {
                        GlobalJournalCardPlaceholderImage(placeholderPainter, onClick = {
                          alterToEdit = alter
                        })
                      } else {
                        KamelImage(
                          {
                            asyncPainterResource(alter.avatarUrl) {
                              coroutineContext = imageScope.coroutineContext
                              requestBuilder {
                                cacheControl("max-age=31536000, immutable")
                              }
                            }
                          },
                          // onLoading = { PlaceholderImage(placeholderPainter) },
                          onFailure = {
                            GlobalJournalCardPlaceholderImage(placeholderPainter, onClick = {
                              alterToEdit = alter
                            })
                          },
                          contentDescription = stringResource(
                            Res.string.name_avatar,
                            alter.name ?: Res.string.unnamed_alter.compose
                          ),
                          modifier = Modifier.size(32.dp).clip(CircleShape).clickable {
                            alterToEdit = alter
                          },
                          animationSpec = effectsSpec()
                        )
                      }
                    }
                  }
                }
                GlobalJournalCardAddAlterButton(addPainter, launchAddAlter = {
                  attachAlterDialogOpen = true
                })
              }
              Spacer(modifier = Modifier.size(8.dp))
              Text(
                text = "${localeFormatNumber(contentState.ensureContent?.length ?: 0)} / ${localeFormatNumber(30_000)}",
                style = MaterialTheme.typography.labelSmall
              )
            }
          }
        }

        if (showUnencryptedWarning) {
          UnencryptedWarningDialog(onDismissRequest = model::dismissUnencryptedWarning)
        }

        if (deleteEntryDialogOpen) {
          DeleteJournalEntryDialog(
            journalEntry = initialEntry!!,
            onDismissRequest = { deleteEntryDialogOpen = false },
            launchDeleteJournalEntry = {
              api.deleteGlobalJournalEntry(it.id)
              component.navigateBack(false)
            }
          )
        }

        if (editTitleDialogOpen) {
          EditJournalTitleDialog(
            initialTitle = title,
            launchEditJournalTitle = model::updateTitle,
            onDismissRequest = { editTitleDialogOpen = false }
          )
        }

        if (attachAlterDialogOpen) {
          AttachAlterDialog(
            existingAlters = validAlters.map { it.id },
            alters = allAlters.ensureData,
            onDismissRequest = { attachAlterDialogOpen = false },
            placeholderPainter = placeholderPainter,
            launchAttachAlter = component::attachAlter,
            attachText = "Attach alter",
            noAltersText = "You have no alters that can be attached to this journal entry.",
            settings = settings
          )
        }

        alterToEdit?.let {
          DetachAlterContextSheet(
            onDismissRequest = { alterToEdit = null },
            alter = it,
            launchDetachAlter = component::detachAlter
          )
        }

        if (colorDialogOpen) {
          UpdateColorDialog(
            initialColor = color,
            updateColor = model::updateColor,
            onDismissRequest = { colorDialogOpen = false },
            settings = settings
          )
        }
      }
    }
  }
}

@Composable
private fun DetachAlterContextSheet(
  onDismissRequest: () -> Unit,
  alter: MyAlter,
  launchDetachAlter: (Int) -> Unit
) {
  InterBottomSheet(
    onDismissRequest = onDismissRequest
  ) {
    BottomSheetListItem(
      icon = rememberVectorPainter(Icons.Rounded.Delete),
      title = "Detach alter"
    ) {
      launchDetachAlter(alter.id)
      onDismissRequest()
    }
  }
}

@Composable
private fun RowScope.TopBarActions(
  ready: Boolean,
  revertChanges: () -> Unit,
  launchChangeColor: () -> Unit,
  launchDeleteEntry: () -> Unit
) {
  var isExpanded by savedState(false)
  IconButton(onClick = launchChangeColor) {
    Icon(
      imageVector = Icons.Rounded.Brush,
      contentDescription = "Change color"
    )
  }
  Box {
    IconButton(onClick = { isExpanded = !isExpanded }) {
      Icon(
        imageVector = Icons.Rounded.MoreVert,
        contentDescription = null
      )
    }
    DropdownMenu(
      expanded = isExpanded,
      onDismissRequest = { isExpanded = false },
      properties = PopupProperties()
    ) {
      SpotlightTooltip(
        title = Res.string.revert_changes.compose,
        description = Res.string.tooltip_revert_changes_desc.compose
      ) {
        DropdownMenuItem(
          text = { Text(Res.string.revert_changes.compose) },
          onClick = {
            revertChanges()
            isExpanded = false
          },
          enabled = ready,
          leadingIcon = {
            Icon(
              Icons.AutoMirrored.Rounded.Undo,
              contentDescription = null
            )
          })
      }
      DropdownMenuItem(
        text = { Text(Res.string.delete_journal_entry.compose) },
        onClick = {
          launchDeleteEntry()
          isExpanded = false
        },
        leadingIcon = {
          Icon(
            Icons.Rounded.Delete,
            tint = MaterialTheme.colorScheme.error,
            contentDescription = null
          )
        }
      )
    }
  }
}
