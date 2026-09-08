package app.interfold.app.ui.compose.screens.main.hometabs.alters.alterview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.interfold.app.Settings
import app.interfold.app.api.ChannelMessage
import app.interfold.app.api.model.AlterJournalEntry
import app.interfold.app.ui.compose.LocalUpdateLazyListState
import app.interfold.app.ui.compose.components.AlterJournalEntryCard
import app.interfold.app.ui.compose.components.ConfirmUnlockJournalEntryDialog
import app.interfold.app.ui.compose.components.CreateJournalEntryDialog
import app.interfold.app.ui.compose.components.DeleteJournalEntryDialog
import app.interfold.app.ui.compose.components.InterSearchBar
import app.interfold.app.ui.compose.components.RecoverEncryptionCard
import app.interfold.app.ui.compose.components.ResetEncryptionCard
import app.interfold.app.ui.compose.components.SetupEncryptionCard
import app.interfold.app.ui.compose.components.shared.BottomSheetListItem
import app.interfold.app.ui.compose.components.shared.IndeterminateProgressSpinner
import app.interfold.app.ui.compose.components.shared.InterBottomSheet
import app.interfold.app.ui.compose.screens.GLOBAL_PADDING
import app.interfold.app.ui.model.main.hometabs.alters.alterview.AlterViewJournalComponent
import app.interfold.app.utils.DevicePlatform
import app.interfold.app.utils.compose
import app.interfold.app.utils.fuse.Fuse
import app.interfold.app.utils.savedState
import app.interfold.app.utils.sortBySimilarity
import app.interfold.app.utils.state
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.delete_journal_entry
import interfoldapp.shared.resources.edit_journal_entry
import interfoldapp.shared.resources.lock_journal_entry
import interfoldapp.shared.resources.pin_journal_entry
import interfoldapp.shared.resources.search_journal_entries
import interfoldapp.shared.resources.unlock_journal_entry
import interfoldapp.shared.resources.unpin_journal_entry

@Composable
fun AlterViewJournalTab(
  component: AlterViewJournalComponent
) {
  val settings by component.settings.collectAsState()
  val api = component.api
  val system by api.systemMe.collectAsState()

  val encryptionIsInitializing by api.encryptionIsInitializing.collectAsState()

  val journalEntries by component.journals.collectAsState()

  var createJournalEntryDialogOpen by state(false)

  var selectedJournalEntry by savedState<AlterJournalEntry?>(null)

  var journalEntryToDelete by savedState<AlterJournalEntry?>(null)
  var journalEntryToUnlock by savedState<AlterJournalEntry?>(null)

  val latestEvent by api.eventFlow.collectAsState(null)

  val lazyListState = rememberLazyListState()
  val updateLazyListState = LocalUpdateLazyListState.current

  val isLoaded by component.model.isLoaded.collectAsState()

  LaunchedEffect(true) {
    updateLazyListState(lazyListState)
    component.updateCreateJournalEntryDialogFun { createJournalEntryDialogOpen = it }
  }

  LaunchedEffect(latestEvent) {
    val hoistedEvent = latestEvent
    if (hoistedEvent !is ChannelMessage.AlterJournalEntryCreated) return@LaunchedEffect

    component.navigateToJournalEntryView(hoistedEvent.entry.id)
  }

  if (journalEntries == null || encryptionIsInitializing || !isLoaded) {
    IndeterminateProgressSpinner()
    return
  }

  if (settings.encryptedEncryptionKey == null) {
    LazyColumn(
      contentPadding = PaddingValues(GLOBAL_PADDING),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      if(system.ensureData.encryptionInitialized) {
        item {
          RecoverEncryptionCard(api = api, settings = component.settings, modifier = Modifier.fillMaxWidth())
        }
        item {
          ResetEncryptionCard(api = api, modifier = Modifier.fillMaxWidth())
        }
      } else {
        item {
          SetupEncryptionCard(api = api, settings = component.settings)
        }
      }
    }
    return
  }

  LazyJournalEntryList(
    journalEntries = journalEntries!!,
    lazyListState = lazyListState,
    launchCreateJournalEntry = { createJournalEntryDialogOpen = true },
    launchEditJournalEntry = {
      if (it.locked) {
        journalEntryToUnlock = it
      } else {
        component.navigateToJournalEntryView(it.id)
      }
    },
    launchOpenJournalEntrySheet = { selectedJournalEntry = it },
    settings = settings
  )

  selectedJournalEntry?.let {
    JournalEntryContextSheet(
      entry = it,
      onDismissRequest = { selectedJournalEntry = null },
      launchEditJournalEntry = {
        if (it.locked) {
          journalEntryToUnlock = it
        } else {
          component.navigateToJournalEntryView(it.id)
        }
      },
      launchLockJournalEntry = { api.lockAlterJournalEntry(it.id) },
      launchUnlockJournalEntry = { api.unlockAlterJournalEntry(it.id) },
      launchPinJournalEntry = { api.pinAlterJournalEntry(it.id) },
      launchUnpinJournalEntry = { api.unpinAlterJournalEntry(it.id) },
      launchDeleteJournalEntry = { journalEntryToDelete = it }
    )
  }

  journalEntryToDelete?.let {
    DeleteJournalEntryDialog(
      journalEntry = it,
      onDismissRequest = { journalEntryToDelete = null },
      launchDeleteJournalEntry = { entry ->
        api.deleteAlterJournalEntry(entry.id)
      }
    )
  }

  journalEntryToUnlock?.let {
    ConfirmUnlockJournalEntryDialog(
      journalEntry = it,
      onDismissRequest = { journalEntryToUnlock = null },
      launchViewJournalEntry = { entry ->
        component.navigateToJournalEntryView(entry.id)
      }
    )
  }

  if (createJournalEntryDialogOpen) {
    CreateJournalEntryDialog(
      onDismissRequest = { createJournalEntryDialogOpen = false },
      launchCreateJournalEntry = {
        api.createAlterJournalEntry(component.model.id, it)
      }
    )
  }
}

@Composable
private fun LazyJournalEntryList(
  journalEntries: List<AlterJournalEntry>,
  lazyListState: LazyListState,
  launchCreateJournalEntry: () -> Unit,
  launchEditJournalEntry: (AlterJournalEntry) -> Unit,
  launchOpenJournalEntrySheet: (AlterJournalEntry) -> Unit,
  settings: Settings
) {
  val searchBarVisible = !DevicePlatform.isWasm && journalEntries.size > 5
  var firstItemNotVisible by state(false)

  val fuse = remember { Fuse() }
  var isSearching by state(false)
  var searchQuery by state("")
  var searchResults by state(journalEntries)

  LaunchedEffect(searchQuery, journalEntries) {
    var setSearchingJob: Job? = null
    val result = if (searchQuery.isBlank()) {
      journalEntries
    } else {
      // Set isSearching if search takes more than 100ms
      setSearchingJob = launch {
        delay(100)
        isSearching = true
      }
      journalEntries.sortBySimilarity({ it.title }, searchQuery, fuse = fuse)
    }

    setSearchingJob?.cancel()
    searchResults = result
    isSearching = false
  }

  LaunchedEffect(lazyListState) {
    snapshotFlow { lazyListState.firstVisibleItemIndex }
      .distinctUntilChanged()
      .collect {
        firstItemNotVisible = it > 0
      }
  }

  Box(
    modifier = Modifier.fillMaxSize()
  ) {
    LazyColumn(
      state = lazyListState,
      modifier = Modifier.fillMaxSize().apply {
        /*if (nestedScrollConnection != null) {
          nestedScroll(nestedScrollConnection)
        }*/
      },
      contentPadding = PaddingValues(GLOBAL_PADDING),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (searchBarVisible) {
        item {
          InterSearchBar(
            searchQuery = searchQuery,
            setSearchQuery = { searchQuery = it },
            isSearching = isSearching,
            placeholderText = Res.string.search_journal_entries.compose,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      if (journalEntries.isEmpty()) {
        item {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(
              defaultElevation = 1.0.dp
            )
          ) {
            Column(
              modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
              Text(
                "You haven't written any journal entries yet!",
                style = MaterialTheme.typography.titleMedium
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                "Write one now to keep track of your day:",
                style = MaterialTheme.typography.bodyMedium.merge(
                  lineHeight = 1.5.em
                )
              )
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = launchCreateJournalEntry,
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary
                )
              ) {
                Text("Create journal entry")
              }
            }
          }
        }
      }

      items(searchResults, key = { it.id }) {
        AlterJournalEntryCard(
          journalEntry = it,
          launchViewJournalEntry = launchEditJournalEntry,
          launchOpenJournalEntrySheet = launchOpenJournalEntrySheet,
          settings = settings
        )
      }

      item {
        Spacer(modifier = Modifier.size(4.dp))
      }
    }

  }
}

@Composable
private fun JournalEntryContextSheet(
  entry: AlterJournalEntry,
  onDismissRequest: () -> Unit,
  launchEditJournalEntry: () -> Unit,
  launchLockJournalEntry: () -> Unit,
  launchUnlockJournalEntry: () -> Unit,
  launchPinJournalEntry: () -> Unit,
  launchUnpinJournalEntry: () -> Unit,
  launchDeleteJournalEntry: () -> Unit,
) {
  InterBottomSheet(
    onDismissRequest = onDismissRequest
  ) {
    BottomSheetListItem(
      imageVector = Icons.Rounded.Edit,
      title = Res.string.edit_journal_entry.compose
    ) {
      launchEditJournalEntry()
      onDismissRequest()
    }

    if (entry.locked) {
      BottomSheetListItem(
        imageVector = Icons.Rounded.LockOpen,
        title = Res.string.unlock_journal_entry.compose
      ) {
        launchUnlockJournalEntry()
        onDismissRequest()
      }
    } else {
      BottomSheetListItem(
        imageVector = Icons.Rounded.Lock,
        title = Res.string.lock_journal_entry.compose
      ) {
        launchLockJournalEntry()
        onDismissRequest()
      }
    }

    if (entry.pinned) {
      BottomSheetListItem(
        imageVector = Icons.Rounded.PushPin,
        title = Res.string.unpin_journal_entry.compose
      ) {
        launchUnpinJournalEntry()
        onDismissRequest()
      }
    } else {
      BottomSheetListItem(
        imageVector = Icons.Rounded.PushPin,
        title = Res.string.pin_journal_entry.compose
      ) {
        launchPinJournalEntry()
        onDismissRequest()
      }
    }


    BottomSheetListItem(
      imageVector = Icons.Rounded.Delete,
      title = Res.string.delete_journal_entry.compose
    ) {
      launchDeleteJournalEntry()
      onDismissRequest()
    }
  }
}
