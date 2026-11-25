package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class EventShiftTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val newYork = TimeZone.of("America/New_York")

    private fun form(
        start: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
        timezone: String? = null,
        isAllDay: Boolean = false,
    ) = EventForm(
        calendarId = 1L,
        title = "Standup",
        isAllDay = isAllDay,
        start = FormTime(start),
        end = FormTime(end),
        timezone = timezone,
    )

    @Test
    fun `shifting to a new start keeps the length`() {
        val original = form()
        val target = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(14, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(14, 30)))
        assertThat(moved.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(15, 30)))
    }

    @Test
    fun `a pinned event is re-derived in its own zone, not the device's`() {
        // 09:00 in New York, opened on a Berlin device.
        val original = form(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
            timezone = "America/New_York",
        )
        // The grid says "15:30 Berlin", which is 09:30 in New York.
        val target = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(15, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.timezone).isEqualTo("America/New_York")
        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 30)))
        assertThat(moved.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 30)))
    }

    @Test
    fun `the instant duration survives a shift across spring forward`() {
        // 2026-03-29 02:00 CET is when Berlin skips to 03:00.
        val original = form(
            start = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(23, 0)),
            end = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)),
        )
        val target = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        // Two real hours from 01:00 CET lands at 04:00 CEST: the hour in
        // between does not exist.
        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)))
        assertThat(moved.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(4, 0)))
        assertThat(moved.end.dateTime.toInstant(berlin) - moved.start.dateTime.toInstant(berlin))
            .isEqualTo(original.end.dateTime.toInstant(berlin) - original.start.dateTime.toInstant(berlin))
    }

    @Test
    fun `a drop into the spring-forward gap resolves forward by the missing hour`() {
        val original = form()
        // 02:30 does not exist on 2026-03-29 in Berlin.
        val target = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(2, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(3, 30)))
    }

    @Test
    fun `a zero-distance drop leaves the form untouched`() {
        // As read from the provider, offsets and all.
        val start = form().start.dateTime.toInstant(berlin)
        val original = form().withTimes(start, form().end.dateTime.toInstant(berlin), berlin)
        assertThat(original.shiftedTo(start, berlin)).isEqualTo(original)
    }

    @Test
    fun `an unparseable pinned zone falls back to the device, like the write path`() {
        val original = form(timezone = "Mars/Olympus")
        val target = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)).toInstant(newYork)

        val moved = original.shiftedTo(target, newYork)

        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)))
    }

    @Test
    fun `an all-day event ignores shiftedTo and moves by whole days instead`() {
        val allDay = form(isAllDay = true)
        val target = LocalDateTime(LocalDate(2026, 7, 1), LocalTime(3, 0)).toInstant(berlin)

        assertThat(allDay.shiftedTo(target, berlin)).isEqualTo(allDay)

        val moved = allDay.shiftedByDays(3, berlin)
        assertThat(moved.start.date).isEqualTo(LocalDate(2026, 6, 14))
        assertThat(moved.end.date).isEqualTo(LocalDate(2026, 6, 14))
        // The placeholder times exist only for a switch back to timed; untouched.
        assertThat(moved.start.time).isEqualTo(allDay.start.time)
    }

    @Test
    fun `a multi-day event keeps its span when shifted by days`() {
        val multiDay = form(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(22, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 13), LocalTime(2, 0)),
        )

        val moved = multiDay.shiftedByDays(-2, berlin)

        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 9), LocalTime(22, 0)))
        assertThat(moved.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(2, 0)))
    }

    @Test
    fun `a timed shift onto a DST changeover keeps the real length, not the wall clock`() {
        // 22:00 Sat -> 04:00 Sun is six real hours, onto the Sunday Berlin
        // springs forward on. Keeping wall clock would write five.
        val overnight = form(
            start = LocalDateTime(LocalDate(2026, 3, 21), LocalTime(22, 0)),
            end = LocalDateTime(LocalDate(2026, 3, 22), LocalTime(4, 0)),
        )

        val moved = overnight.shiftedByDays(7, berlin)

        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 28), LocalTime(22, 0)))
        assertThat(moved.end.dateTime.toInstant(berlin) - moved.start.dateTime.toInstant(berlin))
            .isEqualTo(overnight.end.dateTime.toInstant(berlin) - overnight.start.dateTime.toInstant(berlin))
        // The wall-clock end therefore lands an hour later than a naive +7 days.
        assertThat(moved.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(5, 0)))
    }

    @Test
    fun `shifting by no days is a no-op`() {
        val original = form()
        assertThat(original.shiftedByDays(0, berlin)).isEqualTo(original)
    }

    @Test
    fun `resizing sets each edge on its own`() {
        val original = form()
        val end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 30)).toInstant(berlin)

        val resized = original.resizedTo(original.start.dateTime.toInstant(berlin), end, berlin)

        assertThat(resized.start).isEqualTo(original.start)
        assertThat(resized.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 30)))
    }

    @Test
    fun `a resize past midnight carries the end onto the next day`() {
        val original = form()
        val end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(1, 0)).toInstant(berlin)

        val resized = original.resizedTo(original.start.dateTime.toInstant(berlin), end, berlin)

        assertThat(resized.end.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(1, 0)))
    }

    @Test
    fun `a pinned event is resized in its own zone`() {
        val original = form(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
            timezone = "America/New_York",
        )
        // "15:30 Berlin" is 09:30 in New York.
        val start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(15, 30)).toInstant(berlin)

        val resized = original.resizedTo(start, original.end.dateTime.toInstant(newYork), berlin)

        assertThat(resized.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 30)))
        assertThat(resized.end).isEqualTo(original.end)
    }

    @Test
    fun `a resized all-day event becomes timed`() {
        val allDay = form(isAllDay = true)
        val start = allDay.start.dateTime.toInstant(berlin)

        val timed = allDay.resizedTo(start, start + 1.hours, berlin)
        assertThat(timed.isAllDay).isFalse()
        assertThat(timed.end.dateTime).isEqualTo(allDay.start.dateTime.toInstant(berlin).plus(1.hours).toLocalDateTime(berlin))
    }

    // 02:00 CEST to 02:00 CET on 2026-10-25: one real hour, one wall-clock value.
    private val repeatStart = Instant.parse("2026-10-25T00:00:00Z")
    private val repeatEnd = Instant.parse("2026-10-25T01:00:00Z")

    @Test
    fun `moving an event in the repeated fall-back hour keeps its length`() {
        val original = form().withTimes(repeatStart, repeatEnd, berlin)
        val moved = original.shiftedTo(Instant.parse("2026-10-25T00:30:00Z"), berlin)
        assertThat(moved.startInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        assertThat(moved.endInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
    }

    @Test
    fun `a move onto the second repeated hour stays there`() {
        val target = Instant.parse("2026-10-25T01:30:00Z")
        val moved = form().withTimes(repeatStart, repeatEnd, berlin).shiftedTo(target, berlin)
        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 30)))
        assertThat(moved.startInstant(berlin)).isEqualTo(target)
    }

    @Test
    fun `resolveIn takes the preferred offset only where the hour repeats`() {
        val repeated = LocalDateTime(LocalDate(2026, 10, 25), LocalTime(2, 30))
        val cet = UtcOffset(hours = 1)
        assertThat(repeated.resolveIn(berlin, cet)).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
        assertThat(repeated.resolveIn(berlin, null)).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        // Not one of 14:00's offsets on a summer day: ignored.
        val summer = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(14, 0))
        assertThat(summer.resolveIn(berlin, cet)).isEqualTo(summer.toInstant(berlin))
    }

    @Test
    fun `a day move onto the repeated hour doesn't depend on the source's season`() {
        // 02:30 a week after the fall-back day (CET) and in summer (CEST), both
        // moved onto it: the earlier pass either way, as a timeline drop.
        val first = Instant.parse("2026-10-25T00:30:00Z")
        val november = form().withTimes(
            Instant.parse("2026-11-01T01:30:00Z"),
            Instant.parse("2026-11-01T02:30:00Z"),
            berlin,
        )
        assertThat(november.shiftedByDays(-7, berlin).startInstant(berlin)).isEqualTo(first)
        val summer = form(
            start = LocalDateTime(LocalDate(2026, 10, 18), LocalTime(2, 30)),
            end = LocalDateTime(LocalDate(2026, 10, 18), LocalTime(3, 30)),
        )
        assertThat(summer.shiftedByDays(7, berlin).startInstant(berlin)).isEqualTo(first)
    }

    @Test
    fun `a day move onto the spring-forward gap keeps its wall clock`() {
        // Saturday 02:30 to 03:30, moved onto Sunday 29 March, when 02:30 is skipped.
        val moved = form(
            start = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(2, 30)),
            end = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(3, 30)),
        ).shiftedByDays(1, berlin)
        assertThat(moved.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(2, 30)))
        // It still means 03:30 CEST, an hour long.
        assertThat(moved.startInstant(berlin)).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
        assertThat(moved.endInstant(berlin)).isEqualTo(Instant.parse("2026-03-29T02:30:00Z"))
    }
}
