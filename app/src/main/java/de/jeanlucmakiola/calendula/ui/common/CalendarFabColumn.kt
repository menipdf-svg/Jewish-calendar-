package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/** The standard FAB's size, shared by the selection bar that unrolls from it (#141). */
val FabSize = 56.dp

/** Bottom inset that lets a scrolling view's last row or hour scroll clear of the FAB. */
val FabClearance = 96.dp

/** What the FAB is while an event is selected (#141): the toggle for its action bar. */
@Stable
class FabSelection(
    val expanded: Boolean,
    val onToggle: () -> Unit,
    val bar: @Composable () -> Unit,
)



/**
 * The FAB stack shared by the three calendar views: a persistent "+" to
 * create an event, with the jump-to-today pill appearing above it whenever
 * the view isn't anchored on today. While an event is selected the same FAB,
 * in the same spot, opens and folds the selection's action bar, which unrolls
 * from it towards the start.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarFabColumn(
    todayVisible: Boolean,
    todayText: String,
    onToday: () -> Unit,
    onCreate: () -> Unit,
    selection: FabSelection? = null,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(
            visible = todayVisible && selection == null,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            ExtendedFloatingActionButton(
                onClick = onToday,
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                text = { Text(todayText) },
            )
        }
        // Held past the selection clearing, so the bar has something to fold away.
        val heldBar = rememberHeld(selection?.bar)
        // Held at the FAB's height so the taller bar can't shift the FAB.
        Row(
            modifier = Modifier.height(FabSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = selection?.expanded == true,
                enter = expandHorizontally(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    expandFrom = Alignment.End,
                ) + fadeIn(),
                exit = shrinkHorizontally(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    shrinkTowards = Alignment.End,
                ) + fadeOut(),
            ) {
                heldBar?.invoke()
            }
            val label = stringResource(
                when {
                    selection == null -> R.string.event_edit_new_title
                    selection.expanded -> R.string.event_selection_hide
                    else -> R.string.event_selection_actions
                },
            )
            FloatingActionButton(
                onClick = selection?.onToggle ?: onCreate,
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                FabGlyph(selecting = selection != null, expanded = selection?.expanded == true)
            }
        }
    }
}

/** "+", or with an event selected a chevron that turns to open and fold the bar. */
@Composable
private fun FabGlyph(selecting: Boolean, expanded: Boolean) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val swap by animateFloatAsState(if (selecting) 1f else 0f, spatial, label = "fab-swap")
    val turn by animateFloatAsState(if (expanded) 0f else 180f, spatial, label = "fab-turn")
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                rotationZ = 90f * swap
                scaleX = 1f - 0.5f * swap
                scaleY = 1f - 0.5f * swap
                alpha = (1f - swap).coerceIn(0f, 1f)
            },
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.graphicsLayer {
                rotationZ = turn - 90f * (1f - swap)
                scaleX = 0.5f + 0.5f * swap
                scaleY = 0.5f + 0.5f * swap
                alpha = swap.coerceIn(0f, 1f)
            },
        )
    }
}
