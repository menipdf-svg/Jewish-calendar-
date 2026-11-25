package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.allDayFormTimes
import de.jeanlucmakiola.calendula.domain.forNewEvent
import de.jeanlucmakiola.calendula.domain.normalizedRrule
import de.jeanlucmakiola.calendula.domain.toFormTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Prefill the create form from a single parsed `.ics` event (the "open one
 * event" path). [calendarId] is left null so the form preselects the last-used
 * calendar, exactly like a fresh create — the user confirms the target and
 * reviews everything before saving. Mirrors `EventDetail.toEditForm`'s all-day
 * handling (provider all-day times are UTC midnights with an exclusive end).
 *
 * [ParsedIcsEvent.color] is deliberately not carried over: picking the calendar
 * is the first thing this form asks for, and that clears any colour (a raw ARGB
 * is invalid on a calendar whose account publishes a palette). Bulk import,
 * where the target is known up front, does keep it.
 */
fun ParsedIcsEvent.toEventForm(zone: TimeZone): EventForm {
    val (start, end) = if (isAllDay) {
        allDayFormTimes(this.start.toLocalDateTime(TimeZone.UTC).date, this.end.toLocalDateTime(TimeZone.UTC).date)
    } else {
        this.start.toFormTime(zone) to this.end.toFormTime(zone)
    }
    return EventForm(
        calendarId = null,
        title = summary,
        isAllDay = isAllDay,
        start = start,
        end = end,
        location = location.orEmpty(),
        description = description.orEmpty(),
        reminders = semanticReminderMinutes(),
        availability = availability,
        status = status.forNewEvent(),
        rrule = recurrenceRule.normalizedRrule(),
    )
}
