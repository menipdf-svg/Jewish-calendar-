package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * [live], or after it goes null the last value it had, so an overlay keeps
 * drawing what it showed through its slide-out instead of flashing empty. Never
 * a placeholder: until [live] is first set this is null, so a destination never
 * loads a bogus key on its first frame. Not saved — after a recreation the
 * saveable [live] is the value again.
 */
@Composable
fun <T : Any> rememberHeld(live: T?): T? {
    val held = remember { HeldValue<T>() }
    if (live != null) held.value = live
    return live ?: held.value
}

private class HeldValue<T : Any> {
    var value: T? = null
}
