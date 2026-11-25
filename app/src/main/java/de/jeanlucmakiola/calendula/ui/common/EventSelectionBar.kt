package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/** The selected event's action bar (#141); Edit and Delete only where its calendar allows. */
@Composable
fun EventSelectionBar(
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The FAB's height and colours, so the two read as one control.
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier.height(FabSize),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            if (canEdit) BarAction(Icons.Default.Edit, R.string.event_detail_edit, onEdit)
            BarAction(Icons.Default.ContentCopy, R.string.event_detail_duplicate, onDuplicate)
            BarAction(Icons.Default.Share, R.string.event_detail_share, onShare)
            if (canDelete) BarAction(Icons.Default.Delete, R.string.event_detail_delete, onDelete)
        }
    }
}

/** A drafted block's bar (#153): create the event, or let the draft go. */
@Composable
fun DraftSelectionBar(onCreate: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        modifier = modifier.height(FabSize),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            BarAction(Icons.Default.Close, R.string.event_draft_discard, onDiscard)
            BarAction(Icons.Default.Check, R.string.event_draft_create, onCreate)
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = stringResource(label))
    }
}
