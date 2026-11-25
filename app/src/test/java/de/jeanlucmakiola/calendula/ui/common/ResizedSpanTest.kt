package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/** Where a dragged resize handle leaves an event's ends (#141). */
class ResizedSpanTest {

    // 09:00–10:00, in minutes from its day's midnight.
    private val start = 9 * 60
    private val end = 10 * 60

    @Test
    fun `the bottom edge snaps and leaves the start alone`() {
        assertThat(resizedSpan(ResizeEdge.Bottom, start, end, 11 * 60 + 8f))
            .isEqualTo(start to 11 * 60 + 15)
    }

    @Test
    fun `the top edge snaps and leaves the end alone`() {
        assertThat(resizedSpan(ResizeEdge.Top, start, end, 8 * 60 - 7f))
            .isEqualTo(8 * 60 to end)
    }

    @Test
    fun `an edge stops one snap step short of the other end`() {
        assertThat(resizedSpan(ResizeEdge.Bottom, start, end, 7 * 60f))
            .isEqualTo(start to start + DRAG_SNAP_MINUTES)
        assertThat(resizedSpan(ResizeEdge.Top, start, end, 12 * 60f))
            .isEqualTo(end - DRAG_SNAP_MINUTES to end)
    }

    @Test
    fun `the bottom edge runs on past midnight`() {
        assertThat(resizedSpan(ResizeEdge.Bottom, start, end, 24 * 60 + 90f))
            .isEqualTo(start to 24 * 60 + 90)
    }

    @Test
    fun `the top edge runs back into the day before`() {
        assertThat(resizedSpan(ResizeEdge.Top, start, end, -60f))
            .isEqualTo(-60 to end)
    }

    @Test
    fun `wall clock minutes past either midnight land on the neighbouring day`() {
        val zone = TimeZone.of("Europe/Berlin")
        val day = LocalDate(2026, 10, 9)
        assertThat(wallClockInstant(day, 24 * 60 + 30, zone))
            .isEqualTo(LocalDateTime(LocalDate(2026, 10, 10), LocalTime(0, 30)).toInstant(zone))
        assertThat(wallClockInstant(day, -30, zone))
            .isEqualTo(LocalDateTime(LocalDate(2026, 10, 8), LocalTime(23, 30)).toInstant(zone))
    }

    @Test
    fun `a finger drifting just over a column border keeps the edge on its day`() {
        assertThat(stickyColumn(position = 3.1f, held = 2, lastIndex = 6)).isEqualTo(2)
        assertThat(stickyColumn(position = 1.9f, held = 2, lastIndex = 6)).isEqualTo(2)
    }

    @Test
    fun `a finger well into the next column takes the edge there`() {
        assertThat(stickyColumn(position = 3.4f, held = 2, lastIndex = 6)).isEqualTo(3)
        assertThat(stickyColumn(position = 1.6f, held = 2, lastIndex = 6)).isEqualTo(1)
    }

    @Test
    fun `the first column is the one under the finger`() {
        assertThat(stickyColumn(position = 4.9f, held = null, lastIndex = 6)).isEqualTo(4)
        assertThat(stickyColumn(position = 9f, held = null, lastIndex = 6)).isEqualTo(6)
    }

    @Test
    fun `a finger resting between two slots keeps the one it is on`() {
        // 09:08 rounds to 09:15, but from 09:00 it is not far enough to leave.
        assertThat(stickySnap(9 * 60 + 8f, held = 9 * 60)).isEqualTo(9 * 60)
        assertThat(stickySnap(9 * 60 + 12f, held = 9 * 60)).isEqualTo(9 * 60 + 15)
        assertThat(stickySnap(9 * 60 + 8f, held = null)).isEqualTo(9 * 60 + 15)
    }

    @Test
    fun `an edge dragged into a day past the other end changes nothing`() {
        // The start into the day after a same-day event ends.
        assertThat(resizedSpan(ResizeEdge.Top, start, end, 24 * 60 + 9 * 60f, edgeDay = 1))
            .isEqualTo(start to end)
        // The end into the day before it starts.
        assertThat(resizedSpan(ResizeEdge.Bottom, start, end, -60f, edgeDay = -1))
            .isEqualTo(start to end)
    }

    @Test
    fun `an edge in the event's own days still clamps against the other end`() {
        assertThat(resizedSpan(ResizeEdge.Top, start, end, 12 * 60f, edgeDay = 0))
            .isEqualTo(end - DRAG_SNAP_MINUTES to end)
        // An overnight event's start may move anywhere up to its end's day.
        val overnightEnd = 24 * 60 + 2 * 60
        assertThat(resizedSpan(ResizeEdge.Top, start, overnightEnd, 24 * 60 + 60f, edgeDay = 1))
            .isEqualTo(24 * 60 + 60 to overnightEnd)
    }
}
