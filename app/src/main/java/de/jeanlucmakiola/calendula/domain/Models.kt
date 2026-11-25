package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    /**
     * The system's `Calendars.VISIBLE` flag — the single visibility model,
     * deciding both what Calendula shows and whether this calendar plans
     * reminders (#75). The drawer's filter sheet is a separate in-app declutter.
     */
    val isVisibleInSystem: Boolean,
    /**
     * Whether events in this calendar can be created/edited/deleted
     * (`Calendars.CALENDAR_ACCESS_LEVEL` >= contributor). False for WebCal
     * subscriptions, birthday calendars and other read-only sources.
     */
    val canModifyContents: Boolean = false,
    /**
     * A device-only calendar the app itself owns (`ACCOUNT_TYPE_LOCAL`): it has
     * no sync backend, so the app can rename / recolor / delete it. Synced
     * calendars (Google, DAVx5, …) are managed in their own source app instead.
     */
    val isLocal: Boolean = false,
    /**
     * Free-text note for a local calendar (stored in `CAL_SYNC1`, which the app
     * owns for its own calendars). Always null for synced calendars.
     */
    val description: String? = null,
    /**
     * A special-dates mirror calendar the app manages (birthdays/anniversaries
     * from contacts). Its events' title/date/recurrence are owned by the sync,
     * so it's hidden from the new-event calendar picker and its events lock those
     * fields in the editor. Recognised by a durable provider marker, so it holds
     * even after a backup restore clears the app's stored ids.
     */
    val isManaged: Boolean = false,
    /**
     * Whether the provider keeps this calendar's events on the device
     * (`Calendars.SYNC_EVENTS`), independent of [isVisibleInSystem]. Says
     * nothing about device-local calendars, which can hold events with it off.
     * Read for the "not synced" row label (#76).
     */
    val syncsEvents: Boolean = true,
)

data class EventInstance(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val color: Int,
    val location: String?,
    /**
     * Only search results, which read the series master, fill this in — the
     * Instances query already yields one row per occurrence.
     */
    val isRecurring: Boolean = false,
    /**
     * This device user's own answer to the invitation, as far as the grids care
     * (#180, #230).
     */
    val response: EventResponse = EventResponse.Going,
    /** The event's own Confirmed / Tentative / Cancelled (`Events.STATUS`, #388). */
    val status: EventStatus = EventStatus.Confirmed,
)

/**
 * How this device user stands towards an event's invitation
 * (`Events.SELF_ATTENDEE_STATUS`), reduced to the three cases the calendar
 * surfaces draw differently (#230).
 */
enum class EventResponse {
    /** Your own event, or one you accepted — including a tentative "maybe". */
    Going,

    /** Invited, no answer given yet: drawn as an outline so it reads as still open. */
    Invited,

    /**
     * You answered "no". The event stays on the calendar — it is still an
     * appointment someone expects an answer about — but every surface strikes it
     * through, and it plans no reminders (#180).
     */
    Declined,
}

/**
 * Struck through on every surface and sorted last: an invitation you declined
 * (#180), or an event its organizer cancelled (#388). Neither plans reminders.
 */
val EventInstance.isCrossedOut: Boolean
    get() = response == EventResponse.Declined || status == EventStatus.Cancelled

/**
 * Drawn as an outline, still undecided: an invitation you have not answered
 * (#230), or a tentative event (#388).
 */
val EventInstance.isOutlined: Boolean
    get() = !isCrossedOut && (response == EventResponse.Invited || status == EventStatus.Tentative)

/**
 * Whether this event has finished relative to [now] — its end is at or before
 * the current instant. An in-progress event (already started but not yet ended)
 * is *not* considered ended. All-day events end at the exclusive next-midnight
 * in [zone] rather than the UTC midnight they are stored on, so they only count
 * as ended once their local day is fully over.
 */
fun EventInstance.hasEnded(now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
    localEnd(zone) <= now

/** [EventInstance.end], with an all-day event's UTC midnight moved to local midnight in [zone]. */
fun EventInstance.localEnd(zone: TimeZone): Instant =
    if (isAllDay) end.toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(zone) else end

/**
 * The zone this event's calendar dates live in: the device [zone] for timed
 * events, UTC for all-day ones, whose midnights would otherwise shift day
 * boundaries (#65, #82). Every surface naming an all-day date goes through here.
 */
fun EventInstance.dateZone(zone: TimeZone): TimeZone =
    if (isAllDay) TimeZone.UTC else zone

/** The first calendar day this event occupies. */
fun EventInstance.spanFirstDay(zone: TimeZone): LocalDate =
    start.toLocalDateTime(dateZone(zone)).date

/**
 * The last calendar day this event occupies. An event ending exactly at midnight
 * does not reach into that day, so resolve just before [EventInstance.end].
 */
fun EventInstance.spanLastDay(zone: TimeZone): LocalDate {
    val lastInstant = if (end > start) end - 1.milliseconds else start
    return lastInstant.toLocalDateTime(dateZone(zone)).date
}

/** Whether this event occupies more than one calendar day in [zone]. */
fun EventInstance.spansMultipleDays(zone: TimeZone): Boolean =
    spanFirstDay(zone) != spanLastDay(zone)

/**
 * Whether this event fills whole days in [zone], so the grids and lists lay it
 * out with the all-day events: an all-day event, or a timed one running from
 * local midnight to local midnight (#296). The event itself stays timed.
 */
fun EventInstance.coversWholeDays(zone: TimeZone): Boolean = isAllDay || runsMidnightToMidnight(start, end, zone)

/** Whether a timed span from [start] to [end] runs from local midnight to local midnight in [zone] (#296). */
fun runsMidnightToMidnight(start: Instant, end: Instant, zone: TimeZone): Boolean =
    end > start && start.isStartOfDay(zone) && end.isStartOfDay(zone)

private fun Instant.isStartOfDay(zone: TimeZone): Boolean =
    this == toLocalDateTime(zone).date.atStartOfDayIn(zone)

data class EventDetail(
    val instance: EventInstance,
    val description: String?,
    val organizer: String?,
    val attendees: List<Attendee>,
    val rrule: String?,
    /** Reminders (VALARM) configured on the event, ascending lead time. */
    val reminders: List<Reminder> = emptyList(),
    /** Confirmed / Tentative / Cancelled (`Events.STATUS`). */
    val status: EventStatus = EventStatus.Confirmed,
    /** Busy / Free (`Events.AVAILABILITY`, the iCal TRANSP field). */
    val availability: Availability = Availability.Busy,
    /** Default / Private / Confidential / Public (`Events.ACCESS_LEVEL`). */
    val accessLevel: AccessLevel = AccessLevel.Default,
    /** Raw zone id (`Events.EVENT_TIMEZONE`), e.g. "Europe/Berlin"; null if unset. */
    val eventTimezone: String? = null,
    /** This device user's own response (`Events.SELF_ATTENDEE_STATUS`). */
    val selfStatus: AttendeeStatus = AttendeeStatus.Unknown,
    /**
     * The event's own raw colour (`Events.EVENT_COLOR`), null when the event
     * inherits its calendar's colour. Unlike [EventInstance.color] (which
     * already folds in the calendar fallback for display) this stays null so
     * the edit form can tell "has own colour" from "inherits".
     */
    val eventColor: Int? = null,
    /** The event's `Events.EVENT_COLOR_KEY` (a calendar-palette key), or null. */
    val eventColorKey: String? = null,
    /**
     * True when this row is a modified occurrence of a series (`ORIGINAL_ID` is
     * set) rather than a master, so a reschedule takes the plain whole-row path
     * whatever [rrule] a sync adapter left on it.
     */
    val isException: Boolean = false,
)

/**
 * This event on one occurrence's times. The Events row holds the series start,
 * so a recurring event needs the tapped occurrence pinned to render and share right.
 */
fun EventDetail.atOccurrence(beginMillis: Long, endMillis: Long): EventDetail = copy(
    instance = instance.copy(
        start = Instant.fromEpochMilliseconds(beginMillis),
        end = Instant.fromEpochMilliseconds(endMillis),
    ),
)

/**
 * One selectable event colour published by a calendar's account
 * (`CalendarContract.Colors`, `TYPE_EVENT`): [key] is the account-scoped
 * `COLOR_KEY` written as `EVENT_COLOR_KEY` (so the colour survives sync),
 * [argb] is the swatch it renders as.
 */
data class EventColorOption(val key: String, val argb: Int)

data class Attendee(
    val name: String,
    val email: String?,
    val status: AttendeeStatus,
    /** Organizer / performer / speaker / plain attendee (`ATTENDEE_RELATIONSHIP`). */
    val relationship: AttendeeRelationship = AttendeeRelationship.None,
    /** Required / optional / resource (`ATTENDEE_TYPE`). */
    val type: AttendeeType = AttendeeType.None,
)

data class Reminder(
    /** Lead time before the event start, in minutes. `-1` means the provider default. */
    val minutes: Int,
    val method: ReminderMethod,
)

enum class AttendeeStatus {
    Accepted,
    Declined,
    Tentative,
    NeedsAction,
    Unknown,
}

enum class AttendeeRelationship {
    Organizer,
    Attendee,
    Performer,
    Speaker,
    None,
}

enum class AttendeeType {
    Required,
    Optional,
    Resource,
    None,
}

enum class ReminderMethod {
    Alert,
    Email,
    Sms,
    Alarm,
    Default,
}

enum class EventStatus {
    Confirmed,
    Tentative,
    Cancelled,
}

enum class Availability {
    Busy,
    Free,
    Tentative,
}

enum class AccessLevel {
    Default,
    Public,
    Private,
    Confidential,
}

/**
 * How far a write to a recurring event reaches. Non-recurring events always
 * use [AllEvents] (there is only one).
 */
enum class RecurringWriteScope {
    ThisEvent,
    ThisAndFollowing,
    AllEvents,
}

enum class FailureReason {
    PermissionRevoked,
    NoCalendarsConfigured,
    AllCalendarsHidden,

    /**
     * Calendars exist and are switched on, but none can receive an event: every
     * one is read-only, app-managed, or not synced to this device. Distinct from
     * [AllCalendarsHidden], which a visibility switch fixes.
     */
    NoImportTarget,
    ProviderUnavailable,
    EventNotFound,
    Unknown,
}
