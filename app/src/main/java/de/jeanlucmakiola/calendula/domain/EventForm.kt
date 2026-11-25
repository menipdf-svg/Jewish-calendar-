package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * User input for creating an event (and, from v1.3, editing one). Times are
 * wall-clock values in [timezone]; the data layer translates them to provider
 * millis (all-day events normalise to UTC midnights there).
 */
data class EventForm(
    val calendarId: Long?,
    val title: String = "",
    val isAllDay: Boolean = false,
    val start: FormTime,
    val end: FormTime,
    /**
     * The zone [start]/[end] are wall-clock values in, or null to follow the
     * device — null is not "no zone", it is "whichever zone the device is in
     * when this is saved", which is what an event authored and lived in one
     * place wants. The data layer resolves it at write time and always stamps a
     * concrete `EVENT_TIMEZONE`.
     *
     * A non-null value pins the event to a zone regardless of where the device
     * is, so it keeps tracking that zone's offset across DST. [toEditForm] only
     * sets it when the stored zone differs from the device's, so merely opening
     * a local event never reveals the field — and re-opening a pinned one in
     * another zone round-trips it rather than silently re-anchoring it.
     *
     * Always null for all-day events: those are date-anchored, not zone-anchored
     * (see [EventFormField.Timezone] and the data layer's UTC-midnight rule).
     */
    val timezone: String? = null,
    val location: String = "",
    val description: String = "",
    /** Reminder lead times in minutes before the start, deduplicated. */
    val reminders: List<Int> = emptyList(),
    val availability: Availability = Availability.Busy,
    val accessLevel: AccessLevel = AccessLevel.Default,
    /** Confirmed / Tentative / Cancelled (#388). */
    val status: EventStatus = EventStatus.Confirmed,
    /**
     * Bare RRULE value (`Events.RRULE` convention, no "RRULE:" prefix); null
     * means a one-off event. May hold rules the simple picker can't express —
     * those are kept verbatim until the user picks something else.
     */
    val rrule: String? = null,
    /**
     * The event's own colour, or null to inherit the calendar's colour.
     * [colorKey] is a `Colors.COLOR_KEY` from the calendar account's published
     * event palette (Google, some CalDAV) — written as `EVENT_COLOR_KEY` so it
     * round-trips through sync. When it is null but [color] is set, [color] is
     * a raw ARGB written as `EVENT_COLOR` (local calendars, or synced ones the
     * user opted into despite no palette). [color] mirrors the key's swatch when
     * [colorKey] is set, so the picker can highlight it.
     */
    val colorKey: String? = null,
    val color: Int? = null,
    /**
     * Guests the user has added/kept on the event. Calendula only writes these
     * `Attendees` rows — it has no INTERNET and never sends an invitation
     * itself; whether a guest is notified is decided downstream by the
     * calendar's backend (local: no one; CalDAV/Google: the server/account).
     * Read-only rows the form doesn't model (the organizer, resources) are
     * preserved by the data layer, not carried here.
     */
    val attendees: List<EventAttendee> = emptyList(),
)

/**
 * One end of a form's span: a wall-clock [dateTime] in the form's zone, and the
 * [offset] it was read at when the fall-back hour repeats that wall clock — both
 * of the hour's instants share one [LocalDateTime] (#387). Null everywhere else,
 * so a time changed and changed back compares equal again. A time picked by hand
 * gets its pass from [repicked]; one without an offset reads as the earlier
 * pass. One value, so a time never changes without its pass. See [toFormTime].
 */
data class FormTime(val dateTime: LocalDateTime, val offset: UtcOffset? = null) {
    val date: LocalDate get() = dateTime.date
    val time: LocalTime get() = dateTime.time
}

/**
 * One editable guest: the user controls the [email] (the identity we dedup and
 * write on), an optional display [name], and whether they're [optional] rather
 * than required. Response status and the organizer/resource distinction are not
 * user-editable, so they aren't modelled here.
 */
data class EventAttendee(
    val email: String,
    val name: String = "",
    val optional: Boolean = false,
)

/**
 * The form's optional sections. Which ones show by default is a user setting;
 * the rest unfold behind a "more fields" button. Declaration order is the order
 * they're offered in, so a new constant goes where it belongs on the form, not
 * at the end.
 */
enum class EventFormField {
    Location,
    Description,
    /**
     * Pins the event's wall-clock times to a zone. Offered right after the time
     * fields it qualifies, and suppressed entirely for all-day events, whose
     * dates are deliberately zone-free.
     */
    Timezone,
    Reminders,
    Recurrence,
    Availability,
    Status,
    Visibility,
    Color,
    Attendees,
}

enum class EventFormProblem {
    /** No target calendar — none picked and no writable calendar exists. */
    NoCalendar,
    EndBeforeStart,
    /** The recurrence's UNTIL date lies before the event's first day. */
    RecurrenceEndsBeforeStart,
}

/**
 * Validation; an empty set means the form can be saved. A blank title is
 * allowed (display falls back to "(No title)", matching the provider), and a
 * zero-length timed event is allowed (spec §8: instant events exist).
 */
/**
 * Prefill the edit form from a loaded event. [beginMillis]/[endMillis] are the
 * tapped occurrence's own times (`Instances.BEGIN`/`END`), not the series
 * start — the data layer later turns a time edit into a delta on the series.
 *
 * All-day provider times are UTC midnights with an exclusive end; the form
 * shows the last covered day and keeps placeholder wall-clock times in case
 * the user switches the event to timed.
 *
 * A timed event stored in a zone other than [zone] is prefilled *in its own
 * zone* and keeps it pinned, so the wall-clock the form shows is the one the
 * event means ("the New York 09:00 call") and a later save re-anchors it to the
 * same zone rather than the device's.
 */
fun EventDetail.toEditForm(beginMillis: Long, endMillis: Long, zone: TimeZone): EventForm {
    // All-day events are date-anchored and carry a nominal "UTC" that is an
    // anchor, not a location, so they never pin a zone.
    val pinned = if (instance.isAllDay) {
        null
    } else {
        eventTimezone
            ?.takeIf { it != zone.id }
            // An unparseable id (a malformed sync row) can't be honoured or
            // shown; fall back to the device zone rather than failing the open.
            ?.let { id -> zoneOrNull(id)?.let { id to it } }
    }
    val pinnedZone = pinned?.first
    val formZone = pinned?.second ?: zone
    val begin = Instant.fromEpochMilliseconds(beginMillis)
    val finish = Instant.fromEpochMilliseconds(endMillis)
    val (start, end) = if (instance.isAllDay) {
        allDayFormTimes(begin.toLocalDateTime(TimeZone.UTC).date, finish.toLocalDateTime(TimeZone.UTC).date)
    } else {
        begin.toFormTime(formZone) to finish.toFormTime(formZone)
    }
    return EventForm(
        calendarId = instance.calendarId,
        title = instance.title,
        isAllDay = instance.isAllDay,
        start = start,
        end = end,
        timezone = pinnedZone,
        location = instance.location.orEmpty(),
        description = description.orEmpty(),
        reminders = reminders.map { it.minutes }.distinct().sorted(),
        availability = availability,
        accessLevel = accessLevel,
        status = status,
        rrule = rrule.normalizedRrule(),
        // The provider fills EVENT_COLOR from the key, so [color] is the
        // swatch either way; a null colour means the event inherits its
        // calendar's colour.
        colorKey = eventColorKey,
        color = eventColor,
        // Only editable guests ride in the form: drop the organizer and
        // resource rows (not user-editable) and any without an email (we key
        // edits and dedup on the address). The data layer preserves the rows we
        // don't carry here, so they're never clobbered on save.
        attendees = attendees
            .filter {
                it.relationship != AttendeeRelationship.Organizer &&
                    it.type != AttendeeType.Resource
            }
            .mapNotNull { a ->
                a.email?.takeIf { it.isNotBlank() }?.let { email ->
                    EventAttendee(
                        email = email,
                        name = a.name,
                        optional = a.type == AttendeeType.Optional,
                    )
                }
            },
    )
}

/**
 * What the edit form saw when it loaded — compared against a fresh read at
 * save time to detect external changes (sync, another device) that landed
 * while the form was open. The raw row times ride along because
 * [toEditForm] derives the form's times from the *tapped occurrence*, so
 * re-deriving with the same occurrence would mask an externally moved
 * event. Guests are covered (the form writes editable attendees), so an
 * external attendee change now also trips the conflict check. Still not
 * covered: the user's own response, reminder methods, the
 * organizer/resource rows, and a recurring event's duration.
 */
data class EditSnapshot(
    val form: EventForm,
    /** The raw Events-row times (for recurring events: the series anchor). */
    val rowStart: Instant,
    val rowEnd: Instant,
)

fun EventDetail.toEditSnapshot(beginMillis: Long, endMillis: Long, zone: TimeZone): EditSnapshot =
    EditSnapshot(
        form = toEditForm(beginMillis, endMillis, zone),
        rowStart = instance.start,
        rowEnd = instance.end,
    )

/**
 * Form times for an all-day span stored as UTC midnights with an exclusive
 * end: the last covered day (never before [start]), with placeholder
 * wall-clock times kept in case the user switches the event to timed.
 */
internal fun allDayFormTimes(start: LocalDate, endExclusive: LocalDate?): Pair<FormTime, FormTime> {
    val end = endExclusive?.let { maxOf(start, LocalDate.fromEpochDays(it.toEpochDays() - 1)) } ?: start
    return FormTime(LocalDateTime(start, LocalTime(9, 0))) to FormTime(LocalDateTime(end, LocalTime(10, 0)))
}

/** The bare RRULE value the form edits; blank means none. */
internal fun String?.normalizedRrule(): String? = this?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() }

/**
 * Whether this form may be saved apart from its series, as one modified
 * occurrence or as a split-off "this & following" tail. A cancelled exception
 * row is how Android records a deleted occurrence, and Google reads an inserted
 * cancelled event as deleted, so either would vanish rather than show struck
 * through (#388).
 */
fun EventForm.canSaveApartFromSeries(): Boolean = status != EventStatus.Cancelled

/**
 * The status a newly inserted row may carry. Google reads an inserted cancelled
 * event as deleted, so every new row (a copy, an import, a calendar move or an
 * exception) starts confirmed instead (#388).
 */
fun EventStatus.forNewEvent(): EventStatus = if (this == EventStatus.Cancelled) EventStatus.Confirmed else this

/**
 * Whether this form's times mean something else in [target] — a timed event
 * pinned to another zone. False when there's nothing to disambiguate: an
 * unpinned event (already in [target]), one pinned to [target] itself, an
 * all-day event (no zone), or an unparseable pinned zone.
 *
 * The form edits a pinned event in its own zone, so this is what tells the UI
 * to show the other side of the pair rather than making the user do the arithmetic.
 */
fun EventForm.isPinnedAwayFrom(target: TimeZone): Boolean {
    if (isAllDay) return false
    return resolvedZone(target).id != target.id
}

/**
 * The optional sections that hold a value in [form] — when editing, these
 * must be visible regardless of the user's default-fields setting, or the
 * data they carry would be invisible (though still preserved).
 */
fun EventForm.populatedFields(): Set<EventFormField> = buildSet {
    if (location.isNotBlank()) add(EventFormField.Location)
    if (description.isNotBlank()) add(EventFormField.Description)
    if (timezone != null) add(EventFormField.Timezone)
    if (reminders.isNotEmpty()) add(EventFormField.Reminders)
    if (rrule != null) add(EventFormField.Recurrence)
    if (availability != Availability.Busy) add(EventFormField.Availability)
    if (status != EventStatus.Confirmed) add(EventFormField.Status)
    if (accessLevel != AccessLevel.Default) add(EventFormField.Visibility)
    if (colorKey != null || color != null) add(EventFormField.Color)
    if (attendees.isNotEmpty()) add(EventFormField.Attendees)
}

/**
 * What stops this form from saving. A timed span is checked as instants in the
 * form's zone (else [deviceZone]): one ending in the fall-back hour's repeat can
 * end on an earlier wall clock than it starts (#387).
 */
fun EventForm.problems(deviceZone: TimeZone): Set<EventFormProblem> = buildSet {
    if (calendarId == null) add(EventFormProblem.NoCalendar)
    val endsTooEarly = if (isAllDay) {
        end.date < start.date
    } else {
        val zone = resolvedZone(deviceZone)
        endInstant(zone) < startInstant(zone)
    }
    if (endsTooEarly) add(EventFormProblem.EndBeforeStart)
    // An UNTIL before the first day would make the provider generate zero
    // occurrences — the event would silently vanish from every view.
    val recurrenceEnd = rrule?.let(::parseSimpleRecurrence)?.end
    if (recurrenceEnd is RecurrenceEnd.Until && recurrenceEnd.date < start.date) {
        add(EventFormProblem.RecurrenceEndsBeforeStart)
    }
}

/**
 * A create form seeded from this occurrence, to save a copy as a new,
 * independent event (#52). The recurrence is dropped — a duplicate is a single
 * event. The source calendar is kept only when [canModify], otherwise the id is
 * cleared so the create resolves to a writable calendar, which lets a read-only
 * event be copied into an editable one. [instance]'s own times carry over.
 */
fun EventDetail.toDuplicateForm(canModify: Boolean, zone: TimeZone): EventForm =
    toEditForm(
        beginMillis = instance.start.toEpochMilliseconds(),
        endMillis = instance.end.toEpochMilliseconds(),
        zone = zone,
    ).copy(
        rrule = null,
        calendarId = instance.calendarId.takeIf { canModify },
        status = status.forNewEvent(),
    )
