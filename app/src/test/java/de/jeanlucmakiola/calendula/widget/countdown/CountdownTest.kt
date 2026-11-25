package de.jeanlucmakiola.calendula.widget.countdown

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CountdownTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun event(
        start: Instant,
        end: Instant = start + 1.hours,
        id: Long = 1,
        allDay: Boolean = false,
        response: EventResponse = EventResponse.Going,
        status: EventStatus = EventStatus.Confirmed,
    ) = EventInstance(
        instanceId = id, eventId = id, calendarId = 1, title = "e$id",
        start = start, end = end, isAllDay = allDay, color = 0, location = null, response = response,
        status = status,
    )

    @Test
    fun `the last half hour before the day mark still reads 23 h`() {
        assertThat(countdownLabel(event(now + 23.hours + 40.minutes), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.HOURS, 23))
    }

    @Test
    fun `labels step from days to hours to minutes to now`() {
        assertThat(countdownLabel(event(now + 30.seconds), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.NOW, 0))
        assertThat(countdownLabel(event(now + 24.minutes + 10.seconds), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.MINUTES, 25))
        assertThat(countdownLabel(event(now + 2.hours + 40.minutes), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.HOURS, 3))
        assertThat(countdownLabel(event(now + 1.hours + 10.minutes), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.HOURS, 1))
        // Tomorrow 23:00 is 35 h away but one calendar day.
        assertThat(countdownLabel(event(now + 35.hours), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.DAYS, 1))
        assertThat(countdownLabel(event(now + 12.days), now, zone)).isEqualTo(CountdownLabel(false, CountdownUnit.DAYS, 12))
    }

    @Test
    fun `a running event counts to its end`() {
        val running = event(start = now - 20.minutes, end = now + 10.minutes)
        assertThat(countdownLabel(running, now, zone)).isEqualTo(CountdownLabel(true, CountdownUnit.MINUTES, 10))
    }

    @Test
    fun `the next redraw lands where the label changes`() {
        // "in 25 min" turns into "in 24 min" a minute later.
        assertThat(nextLabelChange(event(now + 25.minutes), now, zone)).isEqualTo(now + 1.minutes)
        // "in 3 h" (2 h 40 min) holds until 2 h 30 min are left.
        assertThat(nextLabelChange(event(now + 2.hours + 40.minutes), now, zone)).isEqualTo(now + 10.minutes)
        // "in 1 h" holds until the last hour, then counts minutes.
        assertThat(nextLabelChange(event(now + 1.hours + 10.minutes), now, zone)).isEqualTo(now + 10.minutes)
        // Days change at midnight.
        assertThat(nextLabelChange(event(now + 5.days), now, zone)).isEqualTo(Instant.parse("2026-10-22T00:00:00Z"))
    }

    @Test
    fun `next mode skips all-day, declined and finished events`() {
        val allDay = event(now + 1.hours, id = 1, allDay = true)
        val declined = event(now + 1.hours, id = 2, response = EventResponse.Declined)
        val over = event(now - 2.hours, now - 1.hours, id = 3)
        val later = event(now + 3.hours, id = 4)
        val running = event(now - 1.hours, now + 2.hours, id = 5)
        assertThat(pickNextEvent(listOf(allDay, declined, over, later), now, zone)?.eventId).isEqualTo(4)
        // A running event ending before the next one starts comes first.
        assertThat(pickNextEvent(listOf(later, running), now, zone)?.eventId).isEqualTo(5)
    }

    @Test
    fun `next-event mode skips a timed event filling whole days`() {
        val wholeDay = event(Instant.parse("2026-10-22T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z"), id = 1)
        val later = event(now + 30.hours, id = 2)
        assertThat(pickNextEvent(listOf(wholeDay, later), now, zone)?.eventId).isEqualTo(2)
    }

    @Test
    fun `a pinned series follows its next occurrence`() {
        val past = event(now - 7.days)
        val next = event(now + 7.days)
        assertThat(pickPinnedOccurrence(listOf(past, next), now, zone)).isEqualTo(next)
        assertThat(pickPinnedOccurrence(listOf(past), now, zone)).isNull()
    }

    @Test
    fun `a pinned series skips cancelled occurrences`() {
        val cancelled = event(now + 7.days, id = 1, status = EventStatus.Cancelled)
        val next = event(now + 14.days, id = 2)
        assertThat(pickPinnedOccurrence(listOf(cancelled, next), now, zone)).isEqualTo(next)
    }

    @Test
    fun `an all-day event counts in local days west of UTC`() {
        val newYork = TimeZone.of("America/New_York")
        // 21 Oct, 20:00 in New York; the vacation is all day on the 23rd.
        val evening = Instant.parse("2026-10-22T00:00:00Z")
        val vacation = event(Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-24T00:00:00Z"), allDay = true)
        val counted = vacation.counted(newYork)
        assertThat(counted.start).isEqualTo(Instant.parse("2026-10-23T04:00:00Z"))
        assertThat(countdownLabel(counted, evening, newYork)).isEqualTo(CountdownLabel(false, CountdownUnit.DAYS, 2))
    }
}
