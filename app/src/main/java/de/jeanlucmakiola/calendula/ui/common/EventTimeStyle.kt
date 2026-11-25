package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.floret.locale.currentLocale

private val TIME_WEIGHT = FontWeight.Normal
private val TIME_TRACKING = 0.sp
private val TIME_SPAN = SpanStyle(fontWeight = TIME_WEIGHT, letterSpacing = TIME_TRACKING)

/**
 * Ink for an event's title, against [SECONDARY_INK_ALPHA] for the time beside or
 * beneath it. Full, so the pair separates on a typeface with no medium weight to
 * step down from — Atkinson Hyperlegible and JetBrains Mono ship regular and bold
 * only, and a user's imported font a single face, so [asEventTime] is a no-op
 * there and the ink is the whole difference (#219).
 */
const val TITLE_INK_ALPHA = 1f

/**
 * [this] set as an event's time rather than as its title: the label styles' medium
 * weight and 0.5sp of tracking left a time reading as part of the title next to it.
 */
fun TextStyle.asEventTime(): TextStyle =
    copy(fontWeight = TIME_WEIGHT, letterSpacing = TIME_TRACKING)

/** Text reading [time], set apart in [timeInk], before [title]. */
fun inlineTimeLabel(time: String?, title: String, timeInk: Color): AnnotatedString =
    buildAnnotatedString {
        if (time != null) {
            withStyle(TIME_SPAN.copy(color = timeInk)) { append(time) }
            append(" ")
        }
        append(title)
    }

/** The widest wall-clock time in either convention: two-digit hour, and a meridiem in 12-hour. */
private const val SAMPLE_HOUR = 12
private const val SAMPLE_MINUTE = 45

/**
 * The widest time the current convention writes, for pricing a time prefix by
 * measurement: it follows the 12/24-hour setting and the locale's own format.
 */
@Composable
fun rememberWidestTimeSample(): String {
    val locale = currentLocale()
    val is24Hour = LocalUse24HourFormat.current
    return remember(is24Hour, locale) { formatTimeOfDay(SAMPLE_HOUR, SAMPLE_MINUTE, is24Hour, locale) }
}
