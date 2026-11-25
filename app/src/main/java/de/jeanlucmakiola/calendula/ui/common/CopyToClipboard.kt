package de.jeanlucmakiola.calendula.ui.common

import android.content.ClipData
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import de.jeanlucmakiola.calendula.R
import kotlinx.coroutines.launch

/** Puts one labelled field on the clipboard. */
fun interface FieldCopier {
    operator fun invoke(label: String, text: String)
}

/**
 * A [FieldCopier] that confirms the copy only where the system doesn't (#195).
 *
 * Android 13 raises its own clipboard chip for every copy, so a snackbar on top
 * of it reads as the app having done the job twice. A failure is always worth a
 * word, though: the clipboard can refuse a very long description outright.
 */
@Composable
fun rememberFieldCopier(snackbarHostState: SnackbarHostState): FieldCopier {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val confirmation = stringResource(R.string.field_copied)
    val failure = stringResource(R.string.field_copy_failed)
    return remember(clipboard, scope, snackbarHostState, confirmation, failure) {
        FieldCopier { label, text ->
            scope.launch {
                val message = runCatching {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
                }.fold(
                    onSuccess = {
                        confirmation.takeIf {
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                        }
                    },
                    onFailure = { failure },
                )
                if (message != null) {
                    // Copying twice in a row shouldn't queue two four-second
                    // confirmations — the newest one wins.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
}

/**
 * Long-press to copy [text] whole, filed on the clipboard under [label].
 *
 * [onTap] carries a field's existing tap action through, announced as
 * [onTapLabel]; a field without one stays unclickable rather than growing a
 * ripple that leads nowhere. The long press is announced through semantics,
 * merging the node it sits on, so a screen reader lands on the field itself
 * and finds the action there — a bare container is never focused.
 *
 * The press is caught ahead of the field's children, so a long press on a
 * link inside the text copies too instead of leaving that stretch dead.
 */
@Composable
fun Modifier.copyOnLongPress(
    label: String,
    text: String,
    copy: FieldCopier,
    onTap: (() -> Unit)? = null,
    onTapLabel: String? = null,
): Modifier {
    val actionLabel = stringResource(R.string.field_copy_action)
    val haptics = LocalHapticFeedback.current
    return longPressAheadOfChildren(label, text, copy) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        copy(label, text)
    }
        .then(if (onTap != null) Modifier.clickable(onClickLabel = onTapLabel, onClick = onTap) else Modifier)
        .semantics(mergeDescendants = true) {
            onLongClick(actionLabel) {
                copy(label, text)
                true
            }
        }
}

/**
 * Run [onLongPress] once a press has held still for the long-press timeout,
 * watching on the initial pass — before the children see the events — and
 * then swallow the rest of that press, so neither a link under the finger nor
 * a tap handler fires on release.
 */
private fun Modifier.longPressAheadOfChildren(vararg keys: Any?, onLongPress: () -> Unit): Modifier =
    pointerInput(*keys) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id }
                    val moved = change != null &&
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    if (change == null || !change.pressed || moved) break
                }
                true
            }
            if (endedEarly != null) return@awaitEachGesture
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
