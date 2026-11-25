package de.jeanlucmakiola.calendula

import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** An [EventInstance] with every field defaulted; [eventId] follows [id]. */
fun testEvent(
    id: Long = 1L,
    eventId: Long = id,
    calendarId: Long = 1L,
    title: String = "Event",
    start: Instant = Instant.fromEpochMilliseconds(0),
    end: Instant = start,
    isAllDay: Boolean = false,
    color: Int = 0,
    location: String? = null,
    isRecurring: Boolean = false,
    response: EventResponse = EventResponse.Going,
): EventInstance = EventInstance(
    instanceId = id,
    eventId = eventId,
    calendarId = calendarId,
    title = title,
    start = start,
    end = end,
    isAllDay = isAllDay,
    color = color,
    location = location,
    isRecurring = isRecurring,
    response = response,
)

/** An all-day event over [from]..[toInclusive], at midnights in [zone] with an exclusive end. */
fun allDayEvent(
    from: LocalDate,
    toInclusive: LocalDate = from,
    id: Long = 1L,
    title: String = "Event",
    color: Int = 0,
    zone: TimeZone = TimeZone.UTC,
    calendarId: Long = 1L,
): EventInstance = testEvent(
    id = id,
    calendarId = calendarId,
    title = title,
    start = from.atTime(0, 0).toInstant(zone),
    end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(zone),
    isAllDay = true,
    color = color,
)

/** A timed event from [date] [startHour]:[minute] to [endDate] [endHour]:[minute] in [zone]. */
fun timedEvent(
    date: LocalDate,
    startHour: Int,
    endHour: Int = startHour + 1,
    zone: TimeZone = TimeZone.UTC,
    id: Long = 1L,
    title: String = "Event",
    color: Int = 0,
    endDate: LocalDate = date,
    minute: Int = 0,
    calendarId: Long = 1L,
): EventInstance = testEvent(
    id = id,
    calendarId = calendarId,
    title = title,
    start = date.atTime(startHour, minute).toInstant(zone),
    end = endDate.atTime(endHour, minute).toInstant(zone),
    color = color,
)

/** An [EventDetail] around [instance] with every other field defaulted. */
fun testDetail(
    instance: EventInstance = testEvent(),
    description: String? = null,
    organizer: String? = null,
    attendees: List<Attendee> = emptyList(),
    rrule: String? = null,
    reminders: List<Reminder> = emptyList(),
    status: EventStatus = EventStatus.Confirmed,
    availability: Availability = Availability.Busy,
    accessLevel: AccessLevel = AccessLevel.Default,
    eventTimezone: String? = null,
    selfStatus: AttendeeStatus = AttendeeStatus.Unknown,
    eventColor: Int? = null,
    eventColorKey: String? = null,
    isException: Boolean = false,
): EventDetail = EventDetail(
    instance = instance,
    description = description,
    organizer = organizer,
    attendees = attendees,
    rrule = rrule,
    reminders = reminders,
    status = status,
    availability = availability,
    accessLevel = accessLevel,
    eventTimezone = eventTimezone,
    selfStatus = selfStatus,
    eventColor = eventColor,
    eventColorKey = eventColorKey,
    isException = isException,
)
