package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** How far the finger may wander during the hold — well under touch slop. */
private val PICKUP_TOLERANCE = 6.dp

/** What a long press on a block turned into. */
enum class PickUp {
    /** Nothing to pick up; the gesture is left alone. */
    None,

    /** Lifted and following the finger. */
    Drag,

    /** Held but not movable — the gesture is swallowed until the finger lifts. */
    Hold,
}

/**
 * Pick an event block up with a long press and drag it, without
 * `detectDragGesturesAfterLongPress`: the stock detector cancels as soon as an
 * ancestor consumes or the finger leaves the block, which a
 * `MIN_EVENT_FRACTION`-tall block loses immediately. Movement before the timeout
 * is deliberately not consumed, so a scroll starting on top of a block survives.
 *
 * [onRelease] is the long press that never moved: the finger lifted within touch
 * slop of where it was picked up, or a [PickUp.Hold] ended. Without it, such a
 * release is a drop like any other. [immediate] also lifts the block as soon as
 * the finger moves past touch slop, with no hold — for a block already selected.
 */
@Composable
fun rememberEventDragSource(
    enabled: Boolean,
    key: Any?,
    onPickUp: (pointerInRoot: Offset, blockInRoot: Offset, size: IntSize) -> PickUp,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onRelease: (() -> Unit)? = null,
    immediate: Boolean = false,
): Modifier = rememberPickUpSurface(
    enabled = enabled,
    key = key,
    immediate = immediate,
    onPickUp = { _, pointerInRoot, nodeInRoot, size -> onPickUp(pointerInRoot, nodeInRoot, size) },
    onMove = onMove,
    onDrop = onDrop,
    onCancel = onCancel,
    onRelease = onRelease,
)

/**
 * The same pickup, for a surface that carries many draggable pieces rather than
 * being one itself — the month grid, whose chips are covered by a full-bleed tap
 * layer. [onPickUp] receives the press position local to this node and answers
 * whether anything is there; false abandons the gesture. [skipClaimedDown] leaves
 * a press a descendant already consumed alone.
 */
@Composable
fun rememberDragSurface(
    enabled: Boolean,
    key: Any?,
    onPickUp: (local: Offset, pointerInRoot: Offset, nodeInRoot: Offset, size: IntSize) -> Boolean,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onRelease: (() -> Unit)? = null,
    skipClaimedDown: Boolean = false,
): Modifier = rememberPickUpSurface(
    enabled = enabled,
    key = key,
    immediate = false,
    onPickUp = { local, pointer, node, size ->
        if (onPickUp(local, pointer, node, size)) PickUp.Drag else PickUp.None
    },
    onMove = onMove,
    onDrop = onDrop,
    onCancel = onCancel,
    onRelease = onRelease,
    skipClaimedDown = skipClaimedDown,
)

@Composable
private fun rememberPickUpSurface(
    enabled: Boolean,
    key: Any?,
    immediate: Boolean,
    onPickUp: (local: Offset, pointerInRoot: Offset, nodeInRoot: Offset, size: IntSize) -> PickUp,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onRelease: (() -> Unit)?,
    skipClaimedDown: Boolean = false,
): Modifier {
    if (!enabled) return Modifier
    val holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    val tolerance = with(LocalDensity.current) { PICKUP_TOLERANCE.toPx() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentPickUp by rememberUpdatedState(onPickUp)
    val currentMove by rememberUpdatedState(onMove)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentRelease by rememberUpdatedState(onRelease)
    // Not a key: the pickup changes it, and a restart would cancel the drag.
    val currentImmediate by rememberUpdatedState(immediate)

    return Modifier
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(key) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // A child that took the press, such as a resize handle, keeps it.
                if (skipClaimedDown && down.isConsumed) return@awaitEachGesture
                val origin = down.position
                val immediate = currentImmediate
                // Null: held still. True: an immediate pickup past slop. False: abandoned.
                val outcome = withTimeoutOrNull(holdMillis) {
                    var result: Boolean? = null
                    while (result == null) {
                        val main = awaitPointerEvent()
                        val change = main.changes.firstOrNull { it.id == down.id }
                        val distance = change?.let { (it.position - origin).getDistance() } ?: 0f
                        result = when {
                            change == null || !change.pressed -> false
                            main.changes.count { it.pressed } > 1 -> false
                            immediate && distance > slop && !change.isConsumed -> {
                                change.consume()
                                true
                            }
                            !immediate && distance > tolerance -> false
                            // An ancestor's claim only becomes visible once the
                            // whole main pass has run, so look again on the final pass.
                            awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed } -> false
                            else -> null
                        }
                    }
                    result
                }
                if (outcome == false) return@awaitEachGesture

                val layout = coordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val took = currentPickUp(
                    down.position,
                    layout.localToRoot(down.position),
                    layout.positionInRoot(),
                    layout.size,
                )
                if (took == PickUp.None) return@awaitEachGesture
                // Moved already, for an immediate pickup: it can't be a release.
                var moved = outcome == true
                var dropped = false
                var released = false
                try {
                    // Driven on the initial pass, which runs parent → child: an
                    // ancestor that outranks us (the pinch) has already consumed
                    // by the time we look, and descendants — the month grid's
                    // full-bleed tap layer — see our consumption.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) {
                            change.consume()
                            if (took == PickUp.Hold || (!moved && currentRelease != null)) {
                                released = true
                            } else {
                                dropped = true
                            }
                            break
                        }
                        if (event.changes.count { it.pressed } > 1) break
                        change.consume()
                        if (took == PickUp.Hold) continue
                        if ((change.position - origin).getDistance() > slop) moved = true
                        coordinates[0]?.takeIf { it.isAttached }
                            ?.let { currentMove(it.localToRoot(change.position)) }
                    }
                } finally {
                    // Also reached when the pointer node is disposed mid-drag —
                    // a cancel, not a drop, and must never write.
                    when {
                        released -> {
                            if (took == PickUp.Drag) currentCancel()
                            currentRelease?.invoke()
                        }
                        dropped -> currentDrop()
                        took == PickUp.Drag -> currentCancel()
                    }
                }
            }
        }
}

/**
 * A lift on pickup, a tick every time the drop target snaps to a new slot, and a
 * confirm each time [drops] counts a real drop.
 */
@Composable
fun DragSnapHaptics(slot: Any?, drops: Int) {
    val haptics = LocalHapticFeedback.current
    val previous = remember { arrayOfNulls<Any>(1) }
    val dropsSeen = remember { intArrayOf(drops) }
    LaunchedEffect(drops) {
        if (drops != dropsSeen[0]) {
            dropsSeen[0] = drops
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    LaunchedEffect(slot) {
        val had = previous[0]
        previous[0] = slot
        when {
            slot == null -> Unit
            had == null -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            had != slot -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}
