package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** The handle's touch target, centred on the edge it moves. */
val ResizeHandleTarget = 48.dp

private val HANDLE_DOT = 16.dp
private val HANDLE_RING = 2.dp

/**
 * A grip on one edge of a selected event (#141), dragged without a hold. No
 * semantics: TalkBack resizes through the event's edit action.
 */
@Composable
fun ResizeHandle(
    color: Color,
    enabled: Boolean,
    onStart: (pointerInRoot: Offset) -> Unit,
    onMove: (pointerInRoot: Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentStart by rememberUpdatedState(onStart)
    val currentMove by rememberUpdatedState(onMove)
    val currentEnd by rememberUpdatedState(onEnd)
    val currentCancel by rememberUpdatedState(onCancel)
    // Not a key: turning it off mid-resize must not cancel that resize.
    val currentEnabled by rememberUpdatedState(enabled)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ResizeHandleTarget)
            .clearAndSetSemantics { }
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!currentEnabled) return@awaitEachGesture
                    down.consume()
                    var started = false
                    var ended = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // Claimed by an ancestor that outranks us — the pinch.
                            if (change.isConsumed) break
                            if (event.changes.count { it.pressed } > 1) break
                            change.consume()
                            if (!change.pressed) {
                                ended = started
                                break
                            }
                            val root = coordinates[0]?.takeIf { it.isAttached }
                                ?.localToRoot(change.position) ?: break
                            if (started) {
                                currentMove(root)
                            } else {
                                started = true
                                currentStart(root)
                            }
                        }
                    } finally {
                        when {
                            ended -> currentEnd()
                            started -> currentCancel()
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .size(HANDLE_DOT)
                .background(color, CircleShape)
                .border(HANDLE_RING, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}
