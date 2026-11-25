package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.repeatedHourOffsetAt
import de.jeanlucmakiola.calendula.timedEvent
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import de.jeanlucmakiola.calendula.ui.week.layoutDay
import de.jeanlucmakiola.calendula.ui.week.minutesPast
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/**
 * What a timeline drop asks for, for the head and the tail of an event that
 * crosses midnight (#253). The tail's top edge is midnight rather than the
 * event's start, so the drop has to take that offset back off.
 */
class TimelineDropTest {

    private val zone = TimeZone.UTC
    private val sat = LocalDate(2026, 9, 5)
    private val sun = sat.plus(1, DateTimeUnit.DAY)

    // Sat 5 Sep 20:00 to Sun 6 Sep 08:00.
    private val overnight = timedEvent(
        sat, 20, 8, zone = zone, title = "Night shift", color = 0xFF112233.toInt(), endDate = sun,
    )

    private fun blockOn(day: LocalDate): TimedBlock =
        layoutDay(listOf(overnight), day, zone).single()

    @Test
    fun `the head block carries no clip offset`() {
        val head = blockOn(sat)
        assertThat(head.startMin).isEqualTo(20 * 60)
        assertThat(head.clipOffsetMinutes(sat, zone)).isEqualTo(0)
    }

    @Test
    fun `the tail block is offset by the part that already ran`() {
        val tail = blockOn(sun)
        assertThat(tail.startMin).isEqualTo(0)
        // 20:00 to midnight is four hours of the event already gone.
        assertThat(tail.clipOffsetMinutes(sun, zone)).isEqualTo(4 * 60)
    }

    @Test
    fun `dropping the head asks for exactly where it landed`() {
        val target = LocalDate(2026, 9, 2)
        val drop = TimelineDrop(overnight, target, 20 * 60, clipOffsetMin = 0, token = 0L)

        assertThat(drop.startInstant(zone)).isEqualTo(target.atTime(20, 0).toInstant(zone))
    }

    @Test
    fun `dropping the tail moves the event by the days the tail travelled`() {
        val tail = blockOn(sun)
        val offset = tail.clipOffsetMinutes(sun, zone)
        // Dragged three days back, same height in the column: Sun 6 -> Thu 3.
        val drop = TimelineDrop(overnight, LocalDate(2026, 9, 3), 0, offset, token = 0L)

        // The event began Sat 5 Sep 20:00, so it now begins Wed 2 Sep 20:00 —
        // the whole event moved three days, and no time of day was invented.
        assertThat(drop.startInstant(zone))
            .isEqualTo(LocalDate(2026, 9, 2).atTime(20, 0).toInstant(zone))
    }

    @Test
    fun `dragging the tail down the column re-times the event across midnight`() {
        val offset = blockOn(sun).clipOffsetMinutes(sun, zone)
        // Tail top pulled from 00:00 to 03:00 on its own day.
        val drop = TimelineDrop(overnight, sun, 3 * 60, offset, token = 0L)

        assertThat(drop.startInstant(zone)).isEqualTo(sat.atTime(23, 0).toInstant(zone))
    }

    @Test
    fun `pulling the tail above its own midnight moves the event earlier`() {
        val offset = blockOn(sun).clipOffsetMinutes(sun, zone)
        // The tail's top edge dragged an hour above the midnight it rests on.
        val drop = TimelineDrop(overnight, sun, -60, offset, token = 0L)

        // Event was Sat 20:00 to Sun 08:00; it now begins an hour earlier.
        assertThat(drop.startInstant(zone)).isEqualTo(sat.atTime(19, 0).toInstant(zone))
    }

    @Test
    fun `a top edge dragged past the next midnight resolves onto the day after`() {
        val drop = TimelineDrop(overnight, sat, 25 * 60, clipOffsetMin = 0, token = 0L)

        assertThat(drop.startInstant(zone)).isEqualTo(sun.atTime(1, 0).toInstant(zone))
    }

    @Test
    fun `a short tail can still be dragged a step earlier`() {
        // 23:50 to 00:05: five minutes of tail, less than one snap step, which
        // a floor pinned at midnight left with nowhere to go (#253).
        assertThat(dragFloorMin(clipOffsetMin = 10, eventSpanMin = 15))
            .isEqualTo(-DRAG_SNAP_MINUTES)
    }

    @Test
    fun `a long tail may rise until its event ends just inside the day`() {
        // Sat 20:00 to Sun 08:00, held by the Sunday half.
        assertThat(dragFloorMin(clipOffsetMin = 4 * 60, eventSpanMin = 12 * 60))
            .isEqualTo(DRAG_SNAP_MINUTES - 8 * 60)
    }

    @Test
    fun `an unclipped block cannot be dragged above its own midnight`() {
        assertThat(dragFloorMin(clipOffsetMin = 0, eventSpanMin = 60)).isEqualTo(0)
    }

    @Test
    fun `a drop across a DST boundary keeps the time of day it was let go at`() {
        // Europe/Berlin springs forward at 02:00 on 29 March 2026, between the
        // event's start and the midnight its tail is cut at.
        val berlin = TimeZone.of("Europe/Berlin")
        val sun = LocalDate(2026, 3, 29)
        val overnightIntoDst = timedEvent(
            LocalDate(2026, 3, 28), 22, 6, zone = berlin, id = 3L, title = "Night shift",
            color = 0xFF112233.toInt(), endDate = sun,
        )
        val tail = layoutDay(listOf(overnightIntoDst), sun, berlin).single()
        val offset = tail.clipOffsetMinutes(sun, berlin)
        // Dropped back onto its own slot a week earlier, clear of the boundary.
        val drop = TimelineDrop(overnightIntoDst, LocalDate(2026, 3, 22), 0, offset, token = 0L)

        assertThat(drop.startInstant(berlin))
            .isEqualTo(LocalDate(2026, 3, 21).atTime(22, 0).toInstant(berlin))
    }

    @Test
    fun `a block that is not clipped is unaffected wherever it sits`() {
        val plain = timedEvent(sat, 9, zone = zone, id = 2L, title = "Standup", color = 0xFF112233.toInt())
        val block = layoutDay(listOf(plain), sat, zone).single()
        assertThat(block.clipOffsetMinutes(sat, zone)).isEqualTo(0)
    }

    private val brussels = TimeZone.of("Europe/Brussels")
    private val fallBack = LocalDate(2026, 10, 25)

    @Test
    fun `a block on the fall-back day carries no clip offset`() {
        // #387: 13:45Z is 14:45 CET, drawn and picked up at 14:45.
        val event = overnight.copy(
            start = Instant.parse("2026-10-25T13:45:00Z"),
            end = Instant.parse("2026-10-25T15:45:00Z"),
        )
        val block = layoutDay(listOf(event), fallBack, brussels).single()
        assertThat(block.clipOffsetMinutes(fallBack, brussels)).isEqualTo(0)
        val drop = TimelineDrop(event, fallBack, block.startMin, clipOffsetMin = 0, token = 0L)
        assertThat(drop.startInstant(brussels)).isEqualTo(event.start)
    }

    @Test
    fun `a tail on the fall-back day is offset by wall-clock time`() {
        val event = overnight.copy(
            start = LocalDate(2026, 10, 24).atTime(22, 0).toInstant(brussels),
            end = fallBack.atTime(6, 0).toInstant(brussels),
        )
        val tail = layoutDay(listOf(event), fallBack, brussels).single()
        val offset = tail.clipOffsetMinutes(fallBack, brussels)
        assertThat(offset).isEqualTo(2 * 60)
        val drop = TimelineDrop(event, fallBack, tail.startMin, clipOffsetMin = offset, token = 0L)
        assertThat(drop.startInstant(brussels)).isEqualTo(event.start)
    }

    @Test
    fun `wall-clock span follows the clock change`() {
        // 01:00 to 04:00 on the fall-back day is four real hours.
        assertThat(wallClockSpanMinutes(fallBack, 60, 4.hours, brussels)).isEqualTo(3 * 60)
        // 01:00 to 04:00 on the spring-forward day is two.
        assertThat(wallClockSpanMinutes(LocalDate(2026, 3, 29), 60, 2.hours, brussels)).isEqualTo(3 * 60)
        assertThat(wallClockSpanMinutes(sat, 20 * 60, 12.hours, zone)).isEqualTo(12 * 60)
    }

    @Test
    fun `a drop in the repeated fall-back hour keeps the event's offset`() {
        // 02:30 CET, the second 02:30 of the day, dragged 15 minutes later.
        val event = overnight.copy(
            start = Instant.parse("2026-10-25T01:30:00Z"),
            end = Instant.parse("2026-10-25T02:30:00Z"),
        )
        val block = layoutDay(listOf(event), fallBack, brussels).single()
        val drop = TimelineDrop(event, fallBack, block.startMin + 15, clipOffsetMin = 0, token = 0L)
        assertThat(drop.startInstant(brussels)).isEqualTo(Instant.parse("2026-10-25T01:45:00Z"))
    }

    @Test
    fun `an ambiguous time without an offset to keep takes the earlier one`() {
        assertThat(wallClockInstant(fallBack, 2 * 60 + 45, brussels))
            .isEqualTo(Instant.parse("2026-10-25T00:45:00Z"))
    }

    @Test
    fun `a start in the spring-forward gap resolves an hour on`() {
        val springForward = LocalDate(2026, 3, 29)
        val landed = wallClockInstant(springForward, 2 * 60 + 30, brussels, kept = brussels.repeatedHourOffsetAt(overnight.start))
        assertThat(landed.minutesPast(springForward, brussels)).isEqualTo(3 * 60 + 30)
        assertThat(wallClockSpanMinutes(springForward, 3 * 60 + 30, 1.hours, brussels)).isEqualTo(60)
    }

    @Test
    fun `a span ending in the repeated fall-back hour reaches that hour's end`() {
        // 02:00 CEST plus one hour is 02:00 CET; 02:30 CEST plus one is 02:30 CET.
        assertThat(wallClockSpanMinutes(fallBack, 2 * 60, 1.hours, brussels)).isEqualTo(60)
        assertThat(wallClockSpanMinutes(fallBack, 2 * 60 + 30, 1.hours, brussels)).isEqualTo(30)
    }

    @Test
    fun `a drop onto the repeated fall-back hour doesn't depend on the event's season`() {
        val slot = 2 * 60 + 30
        val first = Instant.parse("2026-10-25T00:30:00Z")
        val july = Instant.parse("2026-07-01T10:00:00Z")
        val november = Instant.parse("2026-11-10T10:00:00Z")
        assertThat(wallClockInstant(fallBack, slot, brussels, kept = brussels.repeatedHourOffsetAt(july))).isEqualTo(first)
        assertThat(wallClockInstant(fallBack, slot, brussels, kept = brussels.repeatedHourOffsetAt(november))).isEqualTo(first)
        // Later the same day, after the clocks went back: still no say.
        val evening = Instant.parse("2026-10-25T18:00:00Z")
        assertThat(wallClockInstant(fallBack, slot, brussels, kept = brussels.repeatedHourOffsetAt(evening))).isEqualTo(first)
    }

    @Test
    fun `a bottom resize into the repeated hour keeps a second-pass start's pass`() {
        // 02:30 CET (the second pass) to 04:00, its end pulled up to 02:45.
        val event = overnight.copy(
            start = Instant.parse("2026-10-25T01:30:00Z"),
            end = Instant.parse("2026-10-25T03:00:00Z"),
        )
        val drop = resizedSpan(event, ResizeEdge.Bottom, fallBack, 2 * 60 + 30, 15, brussels)
        assertThat(drop.start).isEqualTo(event.start)
        assertThat(drop.end).isEqualTo(Instant.parse("2026-10-25T01:45:00Z"))
    }

    @Test
    fun `a resized edge keeps its own pass over the other edge's`() {
        // 02:30 CEST (the first pass) to 02:15 CET (the second), the start pulled to 02:00.
        val event = overnight.copy(
            start = Instant.parse("2026-10-25T00:30:00Z"),
            end = Instant.parse("2026-10-25T01:15:00Z"),
        )
        val drop = resizedSpan(event, ResizeEdge.Top, fallBack, 2 * 60, 60, brussels)
        assertThat(drop.start).isEqualTo(Instant.parse("2026-10-25T00:00:00Z"))
        assertThat(drop.end).isEqualTo(event.end)
    }

    @Test
    fun `a bottom resize up the repeated hour shortens the event`() {
        // 02:20 CEST to 02:10 CET is drawn 02:20 to 03:00; its end pulled up to 02:50.
        val event = overnight.copy(
            start = Instant.parse("2026-10-25T00:20:00Z"),
            end = Instant.parse("2026-10-25T01:10:00Z"),
        )
        val drop = resizedSpan(event, ResizeEdge.Bottom, fallBack, 2 * 60 + 20, 30, brussels)
        assertThat(drop.end).isEqualTo(Instant.parse("2026-10-25T00:50:00Z"))
    }

    @Test
    fun `a bottom resize to a time the grid draws once lands there`() {
        // 01:00 CEST to 04:00 CET, its end pulled up to 02:15: only the first pass
        // is drawn at 02:15, the second would be drawn down at 03:00.
        val event = overnight.copy(
            start = Instant.parse("2026-10-24T23:00:00Z"),
            end = Instant.parse("2026-10-25T03:00:00Z"),
        )
        val drop = resizedSpan(event, ResizeEdge.Bottom, fallBack, 60, 75, brussels)
        assertThat(drop.end).isEqualTo(Instant.parse("2026-10-25T00:15:00Z"))
    }
}
