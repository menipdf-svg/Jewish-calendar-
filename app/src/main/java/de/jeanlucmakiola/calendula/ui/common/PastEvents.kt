package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The cut-off instant before which a calendar event counts as "completed" and is
 * drawn dimmed in the month/week grids — i.e. the current wall-clock minute when
 * the "dim completed events" setting is on, or `null` when it is off (nothing
 * dims). Provided per grid screen so only the event chips that read it recompose
 * as the minute ticks, mirroring [LocalShowHourGrid] / [LocalUse24HourFormat].
 */
val LocalDimCutoff = compositionLocalOf<Instant?> { null }

/**
 * Whether [this] counts as past where it's drawn on [day]: once it has ended,
 * or on any day before the one [now] falls in, so a multi-day event still
 * running doesn't stay undimmed on the days already behind it.
 */
fun EventInstance.isPastOn(
    day: LocalDate,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Boolean = hasEnded(now, zone) || day < now.toLocalDateTime(zone).date

/** Opacity applied to a completed/past event chip when it is dimmed. */
const val EventDimAlpha = 0.4f

/**
 * How a declined invitation's title is struck through (#180) — the mark every
 * surface uses to say "you answered no", chosen over hiding the event because it
 * is still something the organiser expects you at. Null for everything else, so
 * it drops straight into a `Text`'s `textDecoration`.
 */
fun declinedDecoration(isCrossedOut: Boolean): TextDecoration? =
    if (isCrossedOut) TextDecoration.LineThrough else null

/** [declinedDecoration] for the rows that take styled text rather than a `String`. */
fun declinedTitle(title: String, isCrossedOut: Boolean): AnnotatedString = if (isCrossedOut) {
    AnnotatedString(title, SpanStyle(textDecoration = TextDecoration.LineThrough))
} else {
    AnnotatedString(title)
}

/** [declinedTitle] over already-styled text, e.g. a search hit's marked runs. */
fun declinedTitle(title: AnnotatedString, isCrossedOut: Boolean): AnnotatedString =
    if (!isCrossedOut) {
        title
    } else {
        buildAnnotatedString {
            append(title)
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), 0, title.length)
        }
    }
