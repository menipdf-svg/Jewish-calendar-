package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.floret.components.SnackChipMargin
import kotlinx.coroutines.launch

/** The bottom band the selection bar and FAB cover, margin included. */
val SelectionBarClearance = FabSize + SnackChipMargin

/** The FAB's role while an event is selected (#141): its action bar's toggle. Null otherwise. */
@Composable
fun rememberFabSelection(
    host: ChromeHost,
    viewModel: EventActionsViewModel,
    onEdit: (EventInstance) -> Unit,
    onDuplicate: (EventForm) -> Unit,
    chipShown: Boolean,
    onDismissChip: () -> Unit,
): FabSelection? {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.event_share_chooser_title)

    // What the FAB has been told for this selection, over what the coverage says.
    var chosen by remember(host.selection?.event) { mutableStateOf<Boolean?>(null) }
    // Folded while a chip is up, which then sits beside the FAB as usual.
    val expanded = !chipShown && (chosen ?: !host.selectionCompact)

    val currentEdit by rememberUpdatedState(onEdit)
    val currentDuplicate by rememberUpdatedState(onDuplicate)
    val currentDismissChip by rememberUpdatedState(onDismissChip)
    // Each reads the selection at grant time, which may come after a rotation.
    val onCreate = rememberWriteAction { host.selection?.create?.invoke() }
    val onEditSelected = rememberWriteAction {
        host.selection?.takeIf { it.create == null }?.let { selected ->
            selected.dismiss()
            currentEdit(selected.event)
        }
    }
    val onDuplicateSelected = rememberWriteAction {
        val selected = host.selection?.takeIf { it.create == null } ?: return@rememberWriteAction
        scope.launch {
            val form = viewModel.duplicateForm(selected.event) ?: return@launch
            selected.dismiss()
            currentDuplicate(form)
        }
    }
    val onDeleteSelected = rememberWriteAction {
        host.selection?.takeIf { it.create == null }?.let { viewModel.requestDelete(it.event) }
    }

    val selection = host.selection ?: return null
    if (selection.create != null) {
        return remember(selection, expanded, chipShown) {
            FabSelection(
                expanded = expanded,
                onToggle = {
                    if (chipShown) currentDismissChip()
                    chosen = !expanded
                },
                bar = {
                    DraftSelectionBar(
                        onCreate = onCreate,
                        onDiscard = selection.dismiss,
                    )
                },
            )
        }
    }
    val event = selection.event
    val calendar = calendars[event.calendarId]
    val canEdit = calendar?.canModifyContents == true
    val canDelete = canEdit && calendar?.isManaged == false
    return remember(selection, expanded, chipShown, canEdit, canDelete) {
        FabSelection(
            expanded = expanded,
            onToggle = {
                if (chipShown) currentDismissChip()
                chosen = !expanded
            },
            bar = {
                EventSelectionBar(
                    canEdit = canEdit,
                    canDelete = canDelete,
                    onEdit = onEditSelected,
                    onDuplicate = onDuplicateSelected,
                    onShare = {
                        scope.launch {
                            val uri = viewModel.shareUri(event) ?: return@launch
                            if (!context.sendEventShare(uri, chooserTitle)) viewModel.reportShareFailed()
                        }
                    },
                    onDelete = onDeleteSelected,
                )
            },
        )
    }
}

/** What a failed action on a selected event says, for the move host's chip to show (#141). */
@Composable
fun eventActionFailureMessage(failure: EventActionFailure?): String? = when (failure) {
    null -> null
    EventActionFailure.LoadFailed -> stringResource(R.string.event_action_load_failed)
    EventActionFailure.ShareFailed -> stringResource(R.string.event_share_failed)
    EventActionFailure.DeleteFailed -> stringResource(R.string.event_delete_failed)
    EventActionFailure.WriteDenied -> stringResource(R.string.event_delete_write_denied)
    EventActionFailure.Gone -> stringResource(R.string.event_move_gone)
}

/** The delete confirmation a selected event's Delete brings up (#141). */
@Composable
fun EventSelectionHost(host: ChromeHost, viewModel: EventActionsViewModel) {
    val deletePrompt by viewModel.deletePrompt.collectAsStateWithLifecycle()
    deletePrompt?.let { prompt ->
        DeleteEventDialog(
            isRecurring = prompt.isRecurring,
            onConfirm = { writeScope ->
                host.selection?.takeIf { it.event == prompt.event }?.dismiss()
                viewModel.confirmDelete(writeScope)
            },
            onDismiss = viewModel::cancelDelete,
        )
    }
}
