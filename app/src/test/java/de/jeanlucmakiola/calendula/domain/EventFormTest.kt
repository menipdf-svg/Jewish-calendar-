package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.testDetail
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class EventFormTest {

    private fun form(
        calendarId: Long? = 1L,
        isAllDay: Boolean = false,
        start: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
    ): EventForm = EventForm(calendarId = calendarId, isAllDay = isAllDay, start = FormTime(start), end = FormTime(end))

    @Test
    fun `valid timed form has no problems`() {
        assertThat(form().problems(berlin)).isEmpty()
    }

    @Test
    fun `missing calendar is a problem`() {
        assertThat(form(calendarId = null).problems(berlin))
            .containsExactly(EventFormProblem.NoCalendar)
    }

    @Test
    fun `timed end before start is a problem`() {
        val bad = form(end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)))
        assertThat(bad.problems(berlin)).containsExactly(EventFormProblem.EndBeforeStart)
    }

    @Test
    fun `zero-length timed event is allowed`() {
        val instant = form(end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
        assertThat(instant.problems(berlin)).isEmpty()
    }

    @Test
    fun `all-day single day is allowed even though times match`() {
        val allDay = form(
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
        )
        assertThat(allDay.problems(berlin)).isEmpty()
    }

    @Test
    fun `all-day end date before start date is a problem`() {
        val bad = form(
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 10), LocalTime(0, 0)),
        )
        assertThat(bad.problems(berlin)).containsExactly(EventFormProblem.EndBeforeStart)
    }

    @Test
    fun `problems accumulate`() {
        val bad = form(
            calendarId = null,
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)),
        )
        assertThat(bad.problems(berlin)).containsExactly(
            EventFormProblem.NoCalendar,
            EventFormProblem.EndBeforeStart,
        )
    }

    @Test
    fun `recurrence until before the first day is a problem`() {
        // Days before the start, so it parses to an earlier date in any zone.
        val bad = form().copy(rrule = "FREQ=DAILY;UNTIL=20260601T120000Z")
        assertThat(bad.problems(berlin))
            .containsExactly(EventFormProblem.RecurrenceEndsBeforeStart)
    }

    @Test
    fun `recurrence until on or after the first day is fine`() {
        // Date-only UNTIL parses zone-independently.
        assertThat(form().copy(rrule = "FREQ=DAILY;UNTIL=20260611").problems(berlin)).isEmpty()
        assertThat(form().copy(rrule = "FREQ=WEEKLY").problems(berlin)).isEmpty()
    }

    @Test
    fun `complex rrules are not validated against the start`() {
        // The picker can't have produced this ("second Monday" ordinal BYDAY);
        // it is preserved verbatim and never flagged.
        assertThat(form().copy(rrule = "FREQ=MONTHLY;BYDAY=2MO;UNTIL=20200101").problems(berlin)).isEmpty()
    }

    @Test
    fun `weekly byday rules are validated against the start`() {
        val bad = form().copy(rrule = "FREQ=WEEKLY;BYDAY=MO,FR;UNTIL=20200101")
        assertThat(bad.problems(berlin))
            .containsExactly(EventFormProblem.RecurrenceEndsBeforeStart)
    }

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun detail(
        isAllDay: Boolean = false,
        title: String = "Stand-up",
        location: String? = "Berlin",
        description: String? = "Body",
        rrule: String? = null,
        reminders: List<Reminder> = emptyList(),
        availability: Availability = Availability.Busy,
        accessLevel: AccessLevel = AccessLevel.Default,
        rowStart: Long = 0L,
        rowEnd: Long = 0L,
        attendees: List<Attendee> = emptyList(),
        eventColor: Int? = null,
        eventColorKey: String? = null,
        eventTimezone: String? = null,
    ): EventDetail = testDetail(
        instance = testEvent(
            calendarId = 7L,
            title = title,
            start = Instant.fromEpochMilliseconds(rowStart),
            end = Instant.fromEpochMilliseconds(rowEnd),
            isAllDay = isAllDay,
            location = location,
        ),
        description = description,
        attendees = attendees,
        rrule = rrule,
        reminders = reminders,
        availability = availability,
        accessLevel = accessLevel,
        eventColor = eventColor,
        eventColorKey = eventColorKey,
        eventTimezone = eventTimezone,
    )

    @Test
    fun `toEditForm prefills a timed event from the occurrence times`() {
        // 2026-06-11 is CEST (UTC+2): 08:00Z == 10:00 local.
        val prefilled = detail().toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 90L * 60L * 1000L,
            zone = berlin,
        )
        assertThat(prefilled.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
        assertThat(prefilled.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 30)))
        assertThat(prefilled.isAllDay).isFalse()
        assertThat(prefilled.calendarId).isEqualTo(7L)
        assertThat(prefilled.title).isEqualTo("Stand-up")
        assertThat(prefilled.location).isEqualTo("Berlin")
        assertThat(prefilled.description).isEqualTo("Body")
    }

    @Test
    fun `toEditForm leaves an event in the device zone unpinned`() {
        val prefilled = detail(eventTimezone = "Europe/Berlin").toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        // Same zone as the device: pinning it would only make the picker appear
        // on every ordinary event.
        assertThat(prefilled.timezone).isNull()
        assertThat(prefilled.populatedFields()).doesNotContain(EventFormField.Timezone)
    }

    @Test
    fun `toEditForm pins a foreign zone and shows the times in it`() {
        val prefilled = detail(eventTimezone = "America/New_York").toEditForm(
            beginMillis = 1_781_164_800_000L, // 08:00Z
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        assertThat(prefilled.timezone).isEqualTo("America/New_York")
        // 08:00Z is 10:00 in Berlin but 04:00 in New York — the form shows the
        // event's own wall-clock, which is what a later save re-anchors to.
        assertThat(prefilled.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(4, 0)))
        assertThat(prefilled.populatedFields()).contains(EventFormField.Timezone)
    }

    @Test
    fun `toEditForm never pins a zone on an all-day event`() {
        // All-day rows carry a nominal "UTC" that is an anchor, not a location.
        val prefilled = detail(isAllDay = true, eventTimezone = "UTC").toEditForm(
            beginMillis = LocalDate(2026, 6, 11).toEpochDays() * 86_400_000L,
            endMillis = LocalDate(2026, 6, 12).toEpochDays() * 86_400_000L,
            zone = berlin,
        )
        assertThat(prefilled.timezone).isNull()
    }

    @Test
    fun `toEditForm ignores an unparseable stored zone`() {
        val prefilled = detail(eventTimezone = "Mars/Olympus_Mons").toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        // A malformed sync row must not be honoured, nor fail the open.
        assertThat(prefilled.timezone).isNull()
        assertThat(prefilled.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
    }

    @Test
    fun `isPinnedAwayFrom is false when there is nothing to disambiguate`() {
        val base = EventForm(
            calendarId = 1L,
            start = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(8, 0))),
            end = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(9, 0))),
        )
        // Unpinned: the form's times already are the local times.
        assertThat(base.isPinnedAwayFrom(berlin)).isFalse()
        // Pinned to the target itself: same thing.
        assertThat(base.copy(timezone = "Europe/Berlin").isPinnedAwayFrom(berlin)).isFalse()
        // All-day: date-anchored, so there's no zone conversion to show.
        assertThat(base.copy(isAllDay = true, timezone = "America/New_York").isPinnedAwayFrom(berlin))
            .isFalse()
        // Unparseable: can't convert, mustn't throw.
        assertThat(base.copy(timezone = "Mars/Olympus_Mons").isPinnedAwayFrom(berlin)).isFalse()
        // Pinned elsewhere: worth spelling out.
        assertThat(base.copy(timezone = "America/New_York").isPinnedAwayFrom(berlin)).isTrue()
    }

    @Test
    fun `toEditForm turns the exclusive all-day end into the last covered day`() {
        // 11th..13th = UTC midnights of the 11th and the (exclusive) 14th.
        val prefilled = detail(isAllDay = true).toEditForm(
            beginMillis = LocalDate(2026, 6, 11).toEpochDays() * 86_400_000L,
            endMillis = LocalDate(2026, 6, 14).toEpochDays() * 86_400_000L,
            zone = berlin,
        )
        assertThat(prefilled.start.date).isEqualTo(LocalDate(2026, 6, 11))
        assertThat(prefilled.end.date).isEqualTo(LocalDate(2026, 6, 13))
        assertThat(prefilled.isAllDay).isTrue()
    }

    @Test
    fun `toEditForm sorts and dedupes reminder minutes and strips an RRULE prefix`() {
        val prefilled = detail(
            rrule = "RRULE:FREQ=WEEKLY",
            reminders = listOf(
                Reminder(30, ReminderMethod.Email),
                Reminder(10, ReminderMethod.Alert),
                Reminder(30, ReminderMethod.Alert),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.reminders).containsExactly(10, 30).inOrder()
        assertThat(prefilled.rrule).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `snapshots of an unchanged event are equal`() {
        val a = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val b = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(b).isEqualTo(a)
    }

    @Test
    fun `an external field change makes snapshots differ`() {
        val loaded = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(title = "Stand-up (moved)").toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `an external time move is caught by the row times the form cannot see`() {
        // Both snapshots are taken for the same tapped occurrence, so the
        // *forms* derive identical times — only rowStart/rowEnd betray the move.
        val loaded = detail(rrule = "FREQ=WEEKLY", rowStart = 0L)
            .toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(rrule = "FREQ=WEEKLY", rowStart = 86_400_000L)
            .toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh.form).isEqualTo(loaded.form)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `a reminder-method change the form cannot write does not fake a conflict`() {
        // The form carries reminder minutes only, not the method — so a method
        // flip the form can't express must not read as an external change.
        val loaded = detail(reminders = listOf(Reminder(10, ReminderMethod.Alert)))
            .toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(reminders = listOf(Reminder(10, ReminderMethod.Email)))
            .toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isEqualTo(loaded)
    }

    @Test
    fun `an external guest change now trips the conflict check`() {
        // Attendees are writable now, so they ride in the snapshot: adding a
        // guest externally is a real conflict, not invisible.
        val loaded = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(
            attendees = listOf(Attendee("Ada", "ada@example.org", AttendeeStatus.NeedsAction)),
        ).toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `toEditForm carries editable guests with name email and optional role`() {
        val prefilled = detail(
            attendees = listOf(
                Attendee("Ada", "ada@example.org", AttendeeStatus.Accepted, type = AttendeeType.Required),
                Attendee("", "bob@example.org", AttendeeStatus.NeedsAction, type = AttendeeType.Optional),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.attendees).containsExactly(
            EventAttendee(email = "ada@example.org", name = "Ada", optional = false),
            EventAttendee(email = "bob@example.org", name = "", optional = true),
        ).inOrder()
        assertThat(prefilled.populatedFields()).contains(EventFormField.Attendees)
    }

    @Test
    fun `toEditForm drops the organizer, resources and guests without an email`() {
        val prefilled = detail(
            attendees = listOf(
                Attendee("Org", "org@example.org", AttendeeStatus.Accepted, AttendeeRelationship.Organizer),
                Attendee("Room A", "room@example.org", AttendeeStatus.Accepted, type = AttendeeType.Resource),
                Attendee("No address", null, AttendeeStatus.NeedsAction),
                Attendee("Ada", "ada@example.org", AttendeeStatus.NeedsAction),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.attendees).containsExactly(
            EventAttendee(email = "ada@example.org", name = "Ada", optional = false),
        )
    }

    @Test
    fun `populatedFields reports exactly the sections holding values`() {
        val empty = form().copy(location = "", description = "")
        assertThat(empty.populatedFields()).isEmpty()

        val full = form().copy(
            location = "Berlin",
            description = "Body",
            reminders = listOf(10),
            rrule = "FREQ=DAILY",
            availability = Availability.Free,
            accessLevel = AccessLevel.Private,
            status = EventStatus.Tentative,
            color = 0xFFD50000.toInt(),
        )
        assertThat(full.populatedFields()).containsExactly(
            EventFormField.Location,
            EventFormField.Description,
            EventFormField.Reminders,
            EventFormField.Recurrence,
            EventFormField.Availability,
            EventFormField.Status,
            EventFormField.Visibility,
            EventFormField.Color,
        )
    }

    @Test
    fun `toEditForm carries a palette colour as key plus swatch`() {
        val prefilled = detail(eventColor = 0xFF33B679.toInt(), eventColorKey = "5")
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isEqualTo("5")
        assertThat(prefilled.color).isEqualTo(0xFF33B679.toInt())
        assertThat(prefilled.populatedFields()).contains(EventFormField.Color)
    }

    @Test
    fun `toEditForm carries a raw colour with no key`() {
        val prefilled = detail(eventColor = 0xFF8E24AA.toInt())
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isNull()
        assertThat(prefilled.color).isEqualTo(0xFF8E24AA.toInt())
    }

    @Test
    fun `toEditForm leaves an inheriting event without a colour`() {
        val prefilled = detail()
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isNull()
        assertThat(prefilled.color).isNull()
        assertThat(prefilled.populatedFields()).doesNotContain(EventFormField.Color)
    }

    @Test
    fun `toEditForm keeps which repeated fall-back hour each end is in`() {
        val start = Instant.parse("2026-10-25T00:00:00Z")
        val end = Instant.parse("2026-10-25T01:00:00Z")
        val prefilled = detail().toEditForm(start.toEpochMilliseconds(), end.toEpochMilliseconds(), berlin)
        assertThat(prefilled.start.dateTime).isEqualTo(prefilled.end.dateTime)
        assertThat(prefilled.startInstant(berlin)).isEqualTo(start)
        assertThat(prefilled.endInstant(berlin)).isEqualTo(end)
    }

    @Test
    fun `a span ending in the repeated fall-back hour is valid`() {
        // 02:45 CEST to 02:15 CET: half an hour, though the clock reads backwards.
        val form = form().withTimes(
            Instant.parse("2026-10-25T00:45:00Z"),
            Instant.parse("2026-10-25T01:15:00Z"),
            berlin,
        )
        assertThat(form.end.dateTime).isLessThan(form.start.dateTime)
        assertThat(form.problems(berlin)).isEmpty()
        // Read as the same pass at both ends, it really does end first.
        assertThat(form.copy(start = FormTime(form.start.dateTime), end = FormTime(form.end.dateTime)).problems(berlin))
            .containsExactly(EventFormProblem.EndBeforeStart)
    }

    @Test
    fun `an end picked in the repeated hour moves the way the hand did`() {
        // 02:45 CEST to 02:15 CET.
        val form = form().withTimes(
            Instant.parse("2026-10-25T00:45:00Z"),
            Instant.parse("2026-10-25T01:15:00Z"),
            berlin,
        )
        // Picking the end it already has keeps its pass.
        assertThat(form.withEnd(form.end.dateTime, berlin)).isEqualTo(form)
        // Later on the clock: later in time, still on the second pass.
        assertThat(form.withEnd(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 50)), berlin).endInstant(berlin))
            .isEqualTo(Instant.parse("2026-10-25T01:50:00Z"))
        // Earlier on the clock: earlier in time, not an hour earlier.
        assertThat(form.withEnd(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 5)), berlin).endInstant(berlin))
            .isEqualTo(Instant.parse("2026-10-25T01:05:00Z"))
    }

    @Test
    fun `an end picked in the repeated hour stays after a second-pass start`() {
        // 02:10 CET to 04:00, its end set to 02:40: only the second pass is after the start.
        val form = form().withTimes(
            Instant.parse("2026-10-25T01:10:00Z"),
            Instant.parse("2026-10-25T03:00:00Z"),
            berlin,
        )
        val picked = form.withEnd(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 40)), berlin)
        assertThat(picked.endInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T01:40:00Z"))
        assertThat(picked.problems(berlin)).isEmpty()
    }

    @Test
    fun `a repeated-hour end changed and changed back is the end it was`() {
        // 01:00 CEST to 02:30 CEST, its end set to 03:00 and back.
        val original = form().withTimes(
            Instant.parse("2026-10-24T23:00:00Z"),
            Instant.parse("2026-10-25T00:30:00Z"),
            berlin,
        )
        val repeated = LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 30))
        val away = original.withEnd(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(3, 0)), berlin)
        assertThat(away.withEnd(repeated, berlin, original = original.end)).isEqualTo(original)
        // Without the opened end to go by, coming back from 03:00 reads as the later pass.
        assertThat(away.withEnd(repeated, berlin).endInstant(berlin))
            .isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
    }

    @Test
    fun `a start picked in the repeated hour keeps the length`() {
        // 02:30 CET to 03:30, its start set back to 02:10.
        val form = form().withTimes(
            Instant.parse("2026-10-25T01:30:00Z"),
            Instant.parse("2026-10-25T02:30:00Z"),
            berlin,
        )
        val picked = form.withStart(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 10)), berlin)
        assertThat(picked.startInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T01:10:00Z"))
        assertThat(picked.endInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T02:10:00Z"))
    }

    @Test
    fun `a pinned event's times land in the device zone`() {
        val form = EventForm(
            calendarId = 1L,
            start = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(8, 0))),
            end = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(9, 0))),
            timezone = "America/New_York",
        )
        val zone = form.resolvedZone(berlin)
        // 08:00 New York (EDT, UTC-4) is 14:00 Berlin (CEST, UTC+2).
        assertThat(form.startInstant(zone).toLocalDateTime(berlin))
            .isEqualTo(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(14, 0)))
        assertThat(form.endInstant(zone).toLocalDateTime(berlin))
            .isEqualTo(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(15, 0)))
    }

    @Test
    fun `a pinned event's local time crosses the date line with the offset`() {
        val form = EventForm(
            calendarId = 1L,
            start = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(20, 0))),
            end = FormTime(LocalDateTime(LocalDate(2026, 7, 17), LocalTime(21, 0))),
            timezone = "America/New_York",
        )
        // 20:00 New York is 02:00 the NEXT day in Berlin.
        assertThat(form.startInstant(form.resolvedZone(berlin)).toLocalDateTime(berlin))
            .isEqualTo(LocalDateTime(LocalDate(2026, 7, 18), LocalTime(2, 0)))
    }

    @Test
    fun `a pinned event's local time tracks each zone's own DST`() {
        // Late March the US has sprung forward and Europe hasn't: five hours, not six.
        val form = EventForm(
            calendarId = 1L,
            start = FormTime(LocalDateTime(LocalDate(2026, 3, 20), LocalTime(8, 0))),
            end = FormTime(LocalDateTime(LocalDate(2026, 3, 20), LocalTime(9, 0))),
            timezone = "America/New_York",
        )
        assertThat(form.startInstant(form.resolvedZone(berlin)).toLocalDateTime(berlin))
            .isEqualTo(LocalDateTime(LocalDate(2026, 3, 20), LocalTime(13, 0)))
    }

    @Test
    fun `offsets are kept only for a repeated hour`() {
        val summer = form().withTimes(
            Instant.parse("2026-06-11T08:00:00Z"),
            Instant.parse("2026-06-11T09:00:00Z"),
            berlin,
        )
        assertThat(summer.start.offset).isNull()
        assertThat(summer.end.offset).isNull()
        val repeated = form().withTimes(
            Instant.parse("2026-10-25T00:45:00Z"),
            Instant.parse("2026-10-25T01:15:00Z"),
            berlin,
        )
        assertThat(repeated.start.offset).isEqualTo(UtcOffset(hours = 2))
        assertThat(repeated.end.offset).isEqualTo(UtcOffset(hours = 1))
    }

    @Test
    fun `an end changed and changed back is the form it was`() {
        val original = detail().toEditForm(
            Instant.parse("2026-06-11T08:00:00Z").toEpochMilliseconds(),
            Instant.parse("2026-06-11T09:00:00Z").toEpochMilliseconds(),
            berlin,
        )
        val back = original
            .withEnd(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)), berlin)
            .withEnd(original.end.dateTime, berlin)
        assertThat(back).isEqualTo(original)
    }

    @Test
    fun `a new zone drops the kept pass, the same zone keeps it`() {
        val repeated = form().withTimes(
            Instant.parse("2026-10-25T01:30:00Z"),
            Instant.parse("2026-10-25T02:00:00Z"),
            berlin,
        )
        assertThat(repeated.withTimezone("Europe/Berlin", berlin)).isEqualTo(repeated.copy(timezone = "Europe/Berlin"))
        val moved = repeated.withTimezone("Africa/Lagos", berlin)
        assertThat(moved.start.offset).isNull()
        assertThat(moved.end.offset).isNull()
        // Back on Berlin, a picked 02:30 takes the earlier pass, as any picked time does.
        assertThat(moved.withTimezone(null, berlin).startInstant(berlin))
            .isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
    }

    @Test
    fun `an end picked in the spring-forward gap moves on as a start does`() {
        val form = form(
            start = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)),
            end = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 30)),
        )
        val picked = form.withEnd(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(2, 30)), berlin)
        assertThat(picked.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(3, 30)))
        val started = form.withStart(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(2, 30)), berlin)
        assertThat(started.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(3, 30)))
    }

    @Test
    fun `a cancelled form saves only as the whole series`() {
        assertThat(form().canSaveApartFromSeries()).isTrue()
        assertThat(form().copy(status = EventStatus.Tentative).canSaveApartFromSeries()).isTrue()
        assertThat(form().copy(status = EventStatus.Cancelled).canSaveApartFromSeries()).isFalse()
    }

    @Test
    fun `a copy of a cancelled event starts confirmed, a tentative one stays tentative`() {
        val zone = TimeZone.of("Europe/Berlin")
        assertThat(testDetail(status = EventStatus.Cancelled).toDuplicateForm(canModify = true, zone).status)
            .isEqualTo(EventStatus.Confirmed)
        assertThat(testDetail(status = EventStatus.Tentative).toDuplicateForm(canModify = true, zone).status)
            .isEqualTo(EventStatus.Tentative)
    }
}
