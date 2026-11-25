package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Build a prefilled [EventForm] from an `ACTION_INSERT` intent's extras (issue
 * #30). External apps and widgets (e.g. the Todo Agenda widget) launch the
 * calendar this way to create a new event, passing the fields as
 * [android.provider.CalendarContract] extras. Any field the intent omits falls
 * back to the same defaults the in-app "new event" uses — a timed start at the
 * next full hour, and a placeholder hour for a missing end that the form then
 * stretches to the default-duration setting (the intent is opened as
 * `ImportSource.InsertOpenEnded`, #54). [EventForm.calendarId] is left null so
 * it resolves to the last-used / first-writable calendar, exactly like the
 * `.ics` single-event and plain new-event paths.
 *
 * Pure (no Android types) so it is unit-testable; the intent parsing that reads
 * the extras lives in `MainActivity.insertFormOrNull`.
 */
fun buildInsertEventForm(
    beginMillis: Long? = null,
    endMillis: Long? = null,
    isAllDay: Boolean = false,
    title: String?,
    description: String?,
    location: String? = null,
    rrule: String? = null,
    zone: TimeZone,
    now: Instant,
): EventForm {
    val (start, end) = if (isAllDay) {
        val startDate = beginMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            ?: now.toLocalDateTime(zone).date
        val endExclusive = endMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
        allDayFormTimes(startDate, endExclusive)
    } else {
        val startAt = beginMillis?.let(Instant::fromEpochMilliseconds) ?: nextFullHour(now)
        val endAt = endMillis?.let(Instant::fromEpochMilliseconds)?.takeIf { it >= startAt }
            ?: (startAt + 1.hours)
        startAt.toFormTime(zone) to endAt.toFormTime(zone)
    }
    return EventForm(
        calendarId = null,
        title = title.orEmpty(),
        isAllDay = isAllDay,
        start = start,
        end = end,
        location = location.orEmpty(),
        description = description.orEmpty(),
        // Bare RRULE value (Events.RRULE convention); tolerate a leading "RRULE:"
        // some callers include.
        rrule = rrule.normalizedRrule(),
    )
}

/** The first full hour after [now]: may roll into the next day before midnight. */
internal fun nextFullHour(now: Instant): Instant {
    val hourMillis = 3_600_000L
    return Instant.fromEpochMilliseconds((now.toEpochMilliseconds() / hourMillis + 1) * hourMillis)
}

/** Title and description for text shared or selected into the app (#306). */
data class SharedText(val title: String, val description: String)

/**
 * Split shared text into an event title and description: the share's subject
 * becomes the title and the text the description. Without a subject the text's
 * first non-blank line is the title and the rest the description. A title longer
 * than [maxTitle] is cut, and the full text then stays in the description.
 * Null when there is nothing to use.
 */
fun sharedTextOrNull(subject: String?, text: String?, maxTitle: Int = 255): SharedText? {
    val body = text?.trim().orEmpty()
    val given = subject?.trim()?.takeIf { it.isNotEmpty() }
    val (title, rest) = if (given != null) {
        given to body
    } else {
        val lines = body.lines()
        val first = lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        first to lines.drop(1).joinToString("\n").trim()
    }
    if (title.length <= maxTitle) return SharedText(title, rest)
    val cut = title.take(maxTitle).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
    val full = if (given != null) listOf(given, body).filter { it.isNotEmpty() }.joinToString("\n\n") else body
    return SharedText(cut.trimEnd(), full)
}
