package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

/** A block drafted on an empty timeline slot (#153). */
class DraftBlockTest {

    private val zone = TimeZone.UTC
    private val day = LocalDate(2026, 10, 9)

    private fun draft(startMin: Int, minutes: Int) =
        draftEvent("New event", 0, wallClockInstant(day, startMin, zone), minutes)

    @Test
    fun `a press snaps down to the slot it is in`() {
        assertThat(draftStartMin(9 * 60 + 14f)).isEqualTo(9 * 60)
        assertThat(draftStartMin(9 * 60 + 15f)).isEqualTo(9 * 60 + 15)
    }

    @Test
    fun `a press at the very bottom still leaves a slot before midnight`() {
        assertThat(draftStartMin(24 * 60f)).isEqualTo(24 * 60 - DRAG_SNAP_MINUTES)
    }

    @Test
    fun `the draft is told apart from real events`() {
        assertThat(draft(9 * 60, 60).isDraft).isTrue()
    }

    @Test
    fun `a draft within its day is one block the column wide`() {
        val block = draftBlock(draft(9 * 60, 60), day, zone)!!
        assertThat(block.startMin).isEqualTo(9 * 60)
        assertThat(block.endMin).isEqualTo(10 * 60)
        assertThat(block.lane to block.laneCount).isEqualTo(0 to 1)
    }

    @Test
    fun `a draft past midnight is cut there and carries on the next day`() {
        val event = draft(23 * 60, 120)
        val first = draftBlock(event, day, zone)!!
        val second = draftBlock(event, day.plusDays(1), zone)!!
        assertThat(first.startMin to first.endMin).isEqualTo(23 * 60 to 24 * 60)
        assertThat(second.startMin to second.endMin).isEqualTo(0 to 60)
    }

    @Test
    fun `a draft ending at midnight leaves the next day alone`() {
        assertThat(draftBlock(draft(23 * 60, 60), day.plusDays(1), zone)).isNull()
    }

    @Test
    fun `a draft misses the day before it`() {
        assertThat(draftBlock(draft(9 * 60, 60), day.plusDays(-1), zone)).isNull()
    }

    @Test
    fun `a draft past midnight opens the form with its end on the next day`() {
        val span = wallClockSpan(draft(23 * 60, 120), zone)
        assertThat(span).isEqualTo(WallClockSpan(day, 23 * 60, 25 * 60))
        assertThat(wallClockTime(span.day, span.endMin)).isEqualTo(wallClockTime(day.plusDays(1), 60))
    }

    private fun LocalDate.plusDays(days: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() + days)
}
