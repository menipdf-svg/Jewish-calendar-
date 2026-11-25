package de.jeanlucmakiola.calendula.ui.week

import de.jeanlucmakiola.calendula.ui.common.StripDrag
import de.jeanlucmakiola.calendula.ui.common.startOfWeek
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class WeekLayoutTest {

    private val zone = TimeZone.UTC

    // 2026-06-10 is a Wednesday; its Monday-anchored week starts 2026-06-08.
    private val wed = LocalDate(2026, 6, 10)
    private val mon = LocalDate(2026, 6, 8)
    private val weekDays = (0..6).map { mon.plusDays(it) }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant =
        date.atTime(h, m).toInstant(zone)

    private fun event(
        start: Instant,
        end: Instant,
        allDay: Boolean = false,
        id: Long = 1L,
        title: String = "E",
    ) = testEvent(
        id = id, title = title, start = start, end = end, isAllDay = allDay, color = 0xFF112233.toInt(),
    )

    @Test
    fun `startOfWeek snaps to monday`() {
        assertThat(wed.startOfWeek(DayOfWeek.MONDAY)).isEqualTo(mon)
        assertThat(mon.startOfWeek(DayOfWeek.MONDAY)).isEqualTo(mon)
    }

    @Test
    fun `spanRange spans seven days`() {
        val range = spanRange(mon, 7, zone)
        assertThat(range.start).isEqualTo(at(mon, 0, 0))
        // endInclusive is the last second of day 7 (Sunday 2026-06-14)
        assertThat(range.endInclusive).isEqualTo(LocalDate(2026, 6, 14).atTime(23, 59, 59).toInstant(zone))
    }

    @Test
    fun `coversDay is true for any overlap and false otherwise`() {
        val ev = event(at(wed, 9), at(wed, 10))
        assertThat(ev.coversDay(wed, zone)).isTrue()
        assertThat(ev.coversDay(mon, zone)).isFalse()

        // All-day: UTC midnights, end exclusive. Mon..Tue covers Mon and Tue
        // but not Wed (the Wed-midnight end is exclusive).
        val multiDay = event(at(mon, 0), at(wed, 0), allDay = true)
        assertThat(multiDay.coversDay(mon, zone)).isTrue()
        assertThat(multiDay.coversDay(LocalDate(2026, 6, 9), zone)).isTrue()
        assertThat(multiDay.coversDay(wed, zone)).isFalse()
        assertThat(multiDay.coversDay(LocalDate(2026, 6, 12), zone)).isFalse()
    }

    @Test
    fun `single-day all-day event does not leak into the next day east of UTC`() {
        // A birthday on Wed: the provider stores UTC midnights with an exclusive
        // end (Thu 00:00 UTC). In a zone east of UTC the device-local day must
        // still resolve to Wed only — never Thu. Regression for the all-day
        // event appearing on two days in the views.
        val berlin = TimeZone.of("Europe/Berlin") // UTC+2 in June
        val ev = event(at(wed, 0), at(wed.plusDays(1), 0), allDay = true)
        assertThat(ev.coversDay(wed, berlin)).isTrue()
        assertThat(ev.coversDay(wed.plusDays(1), berlin)).isFalse()
        assertThat(ev.coversDay(wed.plusDays(-1), berlin)).isFalse()
    }

    @Test
    fun `single timed event gets one lane`() {
        val blocks = layoutDay(listOf(event(at(wed, 9), at(wed, 10, 30))), wed, zone)
        assertThat(blocks).hasSize(1)
        val b = blocks.single()
        assertThat(b.startMin).isEqualTo(9 * 60)
        assertThat(b.endMin).isEqualTo(10 * 60 + 30)
        assertThat(b.lane).isEqualTo(0)
        assertThat(b.laneCount).isEqualTo(1)
    }

    @Test
    fun `overlapping events resolve to side-by-side lanes`() {
        val a = event(at(wed, 9), at(wed, 11), id = 1L)
        val b = event(at(wed, 10), at(wed, 12), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone).sortedBy { it.lane }
        assertThat(blocks.map { it.lane }).containsExactly(0, 1)
        assertThat(blocks.all { it.laneCount == 2 }).isTrue()
    }

    @Test
    fun `back-to-back events reuse one lane`() {
        val a = event(at(wed, 9), at(wed, 10), id = 1L)
        val b = event(at(wed, 10), at(wed, 11), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone)
        assertThat(blocks).hasSize(2)
        assertThat(blocks.all { it.lane == 0 && it.laneCount == 1 }).isTrue()
    }

    @Test
    fun `two short entries near midnight sit side by side`() {
        // Both lift to end at midnight, so on real times alone the later one would hide the earlier (#483).
        val a = event(at(wed, 23, 40), at(wed, 23, 41), id = 1L)
        val b = event(at(wed, 23, 55), at(wed, 23, 56), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone).sortedBy { it.lane }
        assertThat(blocks.map { it.event.instanceId }).containsExactly(1L, 2L).inOrder()
        assertThat(blocks.all { it.laneCount == 2 }).isTrue()
    }

    @Test
    fun `a short entry lifted into the event before it takes its own lane`() {
        val a = event(at(wed, 23), at(wed, 23, 45), id = 1L)
        val b = event(at(wed, 23, 58), at(wed, 23, 59), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone)
        assertThat(blocks.map { it.lane }).containsExactly(0, 1)
        assertThat(blocks.all { it.laneCount == 2 }).isTrue()
    }

    @Test
    fun `a short entry clear of the next one keeps a single lane`() {
        val a = event(at(wed, 9), at(wed, 9, 10), id = 1L)
        val b = event(at(wed, 9, 30), at(wed, 10), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone)
        assertThat(blocks.all { it.lane == 0 && it.laneCount == 1 }).isTrue()
    }

    @Test
    fun `a block knows whether its event carries on past midnight`() {
        val overnight = event(at(wed, 23), at(wed.plusDays(1), 1))
        assertThat(layoutDay(listOf(overnight), wed, zone).single().continuesAfter).isTrue()
        assertThat(layoutDay(listOf(overnight), wed.plusDays(1), zone).single().continuesAfter).isFalse()
        val toMidnight = event(at(wed, 23), at(wed.plusDays(1), 0))
        assertThat(layoutDay(listOf(toMidnight), wed, zone).single().continuesAfter).isFalse()
    }

    @Test
    fun `event spanning midnight is clipped to the day`() {
        // Starts the previous evening, ends 02:00 on wed.
        val ev = event(at(mon.plusDays(1), 22), at(wed, 2))
        val blocks = layoutDay(listOf(ev), wed, zone)
        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().startMin).isEqualTo(0)
        assertThat(blocks.single().endMin).isEqualTo(2 * 60)
    }

    @Test
    fun `instant event is kept with zero-length`() {
        val ev = event(at(wed, 12), at(wed, 12))
        val blocks = layoutDay(listOf(ev), wed, zone)
        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().startMin).isEqualTo(12 * 60)
        assertThat(blocks.single().endMin).isEqualTo(12 * 60)
    }

    @Test
    fun `all-day events are kept out of the timed layout`() {
        val ev = event(at(wed, 0), at(wed.plusDays(1), 0), allDay = true)
        assertThat(listOf(ev).splitWholeDays(zone)).isEqualTo(listOf(ev) to emptyList<EventInstance>())
    }

    @Test
    fun `events on other days are dropped`() {
        val ev = event(at(mon, 9), at(mon, 10))
        assertThat(layoutDay(listOf(ev), wed, zone)).isEmpty()
    }

    @Test
    fun `single-day all-day event is a one-column span`() {
        // Wed only: start Wed 00:00, end Thu 00:00.
        val ev = event(at(weekDays[2], 0), at(weekDays[3], 0), allDay = true)
        val spans = layoutAllDay(listOf(ev), weekDays, zone)
        assertThat(spans).hasSize(1)
        val s = spans.single()
        assertThat(s.startCol).isEqualTo(2)
        assertThat(s.endCol).isEqualTo(2)
        assertThat(s.lane).isEqualTo(0)
    }

    @Test
    fun `multi-day all-day event becomes one span across columns`() {
        // Tue..Thu: end Fri 00:00 is exclusive, so Fri is not covered.
        val ev = event(at(weekDays[1], 0), at(weekDays[4], 0), allDay = true)
        val s = layoutAllDay(listOf(ev), weekDays, zone).single()
        assertThat(s.startCol).isEqualTo(1)
        assertThat(s.endCol).isEqualTo(3)
    }

    @Test
    fun `span reaching outside the week is clamped to visible columns`() {
        // Starts two days before Monday, ends Wed 00:00 → covers Mon..Tue.
        val ev = event(at(mon.plusDays(-2), 0), at(weekDays[2], 0), allDay = true)
        val s = layoutAllDay(listOf(ev), weekDays, zone).single()
        assertThat(s.startCol).isEqualTo(0)
        assertThat(s.endCol).isEqualTo(1)
    }

    @Test
    fun `overlapping all-day spans get separate lanes`() {
        val a = event(at(weekDays[0], 0), at(weekDays[3], 0), allDay = true, id = 1L) // Mon..Wed
        val b = event(at(weekDays[1], 0), at(weekDays[4], 0), allDay = true, id = 2L) // Tue..Thu
        val spans = layoutAllDay(listOf(a, b), weekDays, zone)
        assertThat(spans.map { it.lane }.toSet()).isEqualTo(setOf(0, 1))
    }

    @Test
    fun `disjoint all-day spans reuse one lane`() {
        val a = event(at(weekDays[0], 0), at(weekDays[1], 0), allDay = true, id = 1L) // Mon
        val b = event(at(weekDays[3], 0), at(weekDays[4], 0), allDay = true, id = 2L) // Thu
        val spans = layoutAllDay(listOf(a, b), weekDays, zone)
        assertThat(spans.all { it.lane == 0 }).isTrue()
    }

    // Europe/Brussels falls back on 2026-10-25 (25 h) and springs forward on 2026-03-29 (23 h).
    private val brussels = TimeZone.of("Europe/Brussels")

    private fun brusselsEvent(date: LocalDate, from: Pair<Int, Int>, to: Pair<Int, Int>) = event(
        date.atTime(from.first, from.second).toInstant(brussels),
        date.atTime(to.first, to.second).toInstant(brussels),
    )

    @Test
    fun `timed block sits at its wall-clock time on the fall-back day`() {
        val day = LocalDate(2026, 10, 25)
        // The event from #387: 13:45Z is 14:45 CET.
        val ev = event(Instant.parse("2026-10-25T13:45:00Z"), Instant.parse("2026-10-25T15:45:00Z"))
        val b = layoutDay(listOf(ev), day, brussels).single()
        assertThat(b.startMin).isEqualTo(14 * 60 + 45)
        assertThat(b.endMin).isEqualTo(16 * 60 + 45)
    }

    @Test
    fun `timed block sits at its wall-clock time on the spring-forward day`() {
        val day = LocalDate(2026, 3, 29)
        val b = layoutDay(listOf(brusselsEvent(day, 14 to 45, 16 to 45)), day, brussels).single()
        assertThat(b.startMin).isEqualTo(14 * 60 + 45)
        assertThat(b.endMin).isEqualTo(16 * 60 + 45)
    }

    @Test
    fun `late block on the fall-back day is not cut short`() {
        val day = LocalDate(2026, 10, 25)
        val b = layoutDay(listOf(brusselsEvent(day, 22 to 30, 23 to 59)), day, brussels).single()
        assertThat(b.startMin).isEqualTo(22 * 60 + 30)
        assertThat(b.endMin).isEqualTo(23 * 60 + 59)
    }

    @Test
    fun `block running past midnight on the fall-back day reaches the day's end`() {
        val day = LocalDate(2026, 10, 25)
        val ev = event(
            day.atTime(23, 0).toInstant(brussels),
            day.plusDays(1).atTime(1, 0).toInstant(brussels),
        )
        val b = layoutDay(listOf(ev), day, brussels).single()
        assertThat(b.startMin).isEqualTo(23 * 60)
        assertThat(b.endMin).isEqualTo(MINUTES_PER_DAY)
    }

    @Test
    fun `instant event on the fall-back day sits at its wall-clock time`() {
        val day = LocalDate(2026, 10, 25)
        val at = day.atTime(9, 0).toInstant(brussels)
        val b = layoutDay(listOf(event(at, at)), day, brussels).single()
        assertThat(b.startMin).isEqualTo(9 * 60)
        assertThat(b.endMin).isEqualTo(9 * 60)
    }

    @Test
    fun `block ending in the repeated fall-back hour is drawn to that hour's end`() {
        val day = LocalDate(2026, 10, 25)
        // 02:00 CEST to 02:00 CET: one real hour, the same wall-clock time at both ends.
        val ev = event(Instant.parse("2026-10-25T00:00:00Z"), Instant.parse("2026-10-25T01:00:00Z"))
        val b = layoutDay(listOf(ev), day, brussels).single()
        assertThat(b.startMin).isEqualTo(2 * 60)
        assertThat(b.endMin).isEqualTo(3 * 60)
    }

    @Test
    fun `block ending in the repeated fall-back hour leaves the next hour free`() {
        val day = LocalDate(2026, 10, 25)
        // 02:15 CEST to 02:15 CET, then 03:00 CET: an hour apart in real time.
        val repeat = event(Instant.parse("2026-10-25T00:15:00Z"), Instant.parse("2026-10-25T01:15:00Z"))
        val next = event(Instant.parse("2026-10-25T02:00:00Z"), Instant.parse("2026-10-25T03:00:00Z"))
        val blocks = layoutDay(listOf(repeat, next), day, brussels)
        assertThat(blocks.map { it.startMin to it.endMin })
            .containsExactly(2 * 60 + 15 to 3 * 60, 3 * 60 to 4 * 60)
        assertThat(blocks.all { it.lane == 0 && it.laneCount == 1 }).isTrue()
    }

    @Test
    fun `minutesPast reads wall clock either side of the day`() {
        val day = LocalDate(2026, 10, 25)
        assertThat(Instant.parse("2026-10-24T21:00:00Z").minutesPast(day, brussels)).isEqualTo(-60)
        assertThat(Instant.parse("2026-10-25T13:45:00Z").minutesPast(day, brussels)).isEqualTo(14 * 60 + 45)
        assertThat(Instant.parse("2026-10-25T23:30:00Z").minutesPast(day, brussels))
            .isEqualTo(MINUTES_PER_DAY + 30)
    }

    @Test
    fun `a block ending in the repeated fall-back hour is labelled with its own end`() {
        val day = LocalDate(2026, 10, 25)
        // 02:30 CEST to 02:30 CET: drawn to the repeated hour's end, but it ends at 02:30.
        val ev = event(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:30:00Z"))
        val b = layoutDay(listOf(ev), day, brussels).single()
        assertThat(b.endMin).isEqualTo(3 * 60)
        assertThat(b.labelEndMin(day, brussels)).isEqualTo(2 * 60 + 30)
    }

    @Test
    fun `an ordinary block is labelled with its drawn end`() {
        val day = LocalDate(2026, 10, 25)
        val b = layoutDay(listOf(brusselsEvent(day, 14 to 45, 16 to 45)), day, brussels).single()
        assertThat(b.labelEndMin(day, brussels)).isEqualTo(b.endMin)
    }

    private fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)

    @Test
    fun `a longer run into the repeated fall-back hour is drawn no shorter`() {
        val day = LocalDate(2026, 10, 25)
        // 02:10 CEST to 02:05 CET (55 min) and to 02:20 CET (70 min): both to 03:00.
        val shorter = event(Instant.parse("2026-10-25T00:10:00Z"), Instant.parse("2026-10-25T01:05:00Z"))
        val longer = event(Instant.parse("2026-10-25T00:10:00Z"), Instant.parse("2026-10-25T01:20:00Z"), id = 2L)
        assertThat(layoutDay(listOf(shorter), day, brussels).single().endMin).isEqualTo(3 * 60)
        assertThat(layoutDay(listOf(longer), day, brussels).single().endMin).isEqualTo(3 * 60)
        // A run that starts on the second pass reads its plain wall clock.
        val second = event(Instant.parse("2026-10-25T01:10:00Z"), Instant.parse("2026-10-25T01:40:00Z"))
        assertThat(layoutDay(listOf(second), day, brussels).single().endMin).isEqualTo(2 * 60 + 40)
    }

    @Test
    fun `a timed event from midnight to midnight stays out of the time grid`() {
        val ev = testEvent(start = at(wed, 0), end = at(wed.plus(1, DateTimeUnit.DAY), 0))

        assertThat(listOf(ev).splitWholeDays(zone)).isEqualTo(listOf(ev) to emptyList<EventInstance>())
    }

    @Test
    fun `a bar held over another day makes room there`() {
        val held = event(at(mon, 0), at(mon.plusDays(1), 0), allDay = true, id = 1, title = "A")
        val there = event(at(wed, 0), at(wed.plusDays(1), 0), allDay = true, id = 2, title = "B")
        val state = WeekUiState.Success(
            weekStart = mon, today = mon, days = weekDays,
            allDaySpans = layoutAllDay(listOf(held, there), weekDays, zone), timedByDay = emptyMap(),
        )
        assertThat(state.allDaySpans.map { it.lane }).containsExactly(0, 0)

        val preview = state.withStripPreview(listOf(StripDrag(held, dayShift = 2)), zone).allDaySpans
        val moved = preview.single { it.event.instanceId == held.instanceId }

        assertThat(moved.event).isEqualTo(held)
        assertThat(moved.startCol).isEqualTo(2)
        assertThat(preview.map { it.lane }).containsExactly(0, 1)
        assertThat(state.withStripPreview(listOf(StripDrag(held, dayShift = 2, overGrid = true)), zone)).isSameInstanceAs(state)
    }
}
