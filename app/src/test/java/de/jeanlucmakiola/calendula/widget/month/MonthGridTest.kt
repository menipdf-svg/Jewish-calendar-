package de.jeanlucmakiola.calendula.widget.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.allDayEvent
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.timedEvent
import de.jeanlucmakiola.calendula.ui.month.MonthWeek
import de.jeanlucmakiola.calendula.ui.month.layoutMonthWeeks
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test

/** How the month widget seats a week's events into lanes (#372). */
class MonthGridTest {

    private val zone = TimeZone.UTC
    private val jul26 = YearMonth(2026, Month.JULY)

    /** Monday Jul 6 to Sunday Jul 12. */
    private val monday = LocalDate(2026, 7, 6)

    private fun weeks(events: List<EventInstance>): List<MonthWeek> =
        layoutMonthWeeks(jul26, DayOfWeek.MONDAY, events, zone)

    /** July 2026 with [perDay] timed events on every day of the grid. */
    private fun busyWeeks(perDay: Int): List<MonthWeek> {
        val first = LocalDate(2026, 6, 29)
        var id = 0L
        return weeks(
            (0 until 42).flatMap { offset ->
                val day = first.plus(offset, DateTimeUnit.DAY)
                (1..perDay).map { hour -> timedEvent(day, hour, zone = zone, id = ++id, title = "E$id") }
            },
        )
    }

    @Test
    fun `a week that fits keeps every lane`() {
        val layouts = fitWeeks(busyWeeks(perDay = 2), LaneFit(3))
        assertThat(layouts.map { it.laneCap }.distinct()).containsExactly(3)
        assertThat(layouts.all { it.overflow.isEmpty() }).isTrue()
    }

    @Test
    fun `a week that doesn't fit gives its last lane to the overflow`() {
        val layouts = fitWeeks(busyWeeks(perDay = 5), LaneFit(3))
        assertThat(layouts.map { it.laneCap }.distinct()).containsExactly(2)
        assertThat(layouts.all { it.overflow.isNotEmpty() }).isTrue()
    }

    @Test
    fun `a taller week fits more lanes`() {
        assertThat(lanesFitting(19.dp, fontScale = 1f)).isEqualTo(LaneFit(0))
        assertThat(lanesFitting(50.dp, fontScale = 1f)).isEqualTo(LaneFit(2))
        assertThat(lanesFitting(100.dp, fontScale = 1f)).isEqualTo(LaneFit(5))
    }

    @Test
    fun `a row too short for a full lane gets a compact one`() {
        assertThat(lanesFitting(34.dp, fontScale = 1f)).isEqualTo(LaneFit(1, compact = true))
        assertThat(lanesFitting(32.dp, fontScale = 1f)).isEqualTo(LaneFit(1, compact = true))
        assertThat(lanesFitting(31.dp, fontScale = 1f)).isEqualTo(LaneFit(0))
        assertThat(lanesFitting(35.dp, fontScale = 1f)).isEqualTo(LaneFit(1))
    }

    @Test
    fun `no compact lane at a large font scale`() {
        assertThat(lanesFitting(34.dp, fontScale = 1.1f)).isEqualTo(LaneFit(0))
    }

    @Test
    fun `a single lane shows counts on busy days`() {
        val layouts = fitWeeks(busyWeeks(perDay = 5), LaneFit(1))
        assertThat(layouts.all { it.mixedLane == List(7) { GridCell<LaneItem>(LaneItem.Count(5), 1) } }).isTrue()
    }

    @Test
    fun `a single lane keeps bars on days that fit`() {
        val lone = timedEvent(monday.plus(1, DateTimeUnit.DAY), 9, zone = zone, id = 1, title = "Lone")
        val busy = (2L..4L).map { timedEvent(monday.plus(3, DateTimeUnit.DAY), it.toInt() + 8, zone = zone, id = it, title = "B$it") }
        val week = fitWeeks(weeks(listOf(lone) + busy), LaneFit(1, compact = true))[1]
        assertThat(week.compact).isTrue()
        assertThat(week.mixedLane).containsExactly(
            GridCell<LaneItem>(null, 1),
            GridCell<LaneItem>(LaneItem.Bar(lone), 1),
            GridCell<LaneItem>(null, 1),
            GridCell<LaneItem>(LaneItem.Count(3), 1),
            GridCell<LaneItem>(null, 3),
        ).inOrder()
    }

    @Test
    fun `a single lane cuts a multi-day bar around a busy day`() {
        val trip = allDayEvent(monday, monday.plus(3, DateTimeUnit.DAY), id = 1, title = "Trip")
        val busy = timedEvent(monday.plus(1, DateTimeUnit.DAY), 9, zone = zone, id = 2, title = "Busy")
        val week = fitWeeks(weeks(listOf(trip, busy)), LaneFit(1))[1]
        assertThat(week.mixedLane).containsExactly(
            GridCell<LaneItem>(LaneItem.Bar(trip), 1),
            GridCell<LaneItem>(LaneItem.Count(2), 1),
            GridCell<LaneItem>(LaneItem.Bar(trip), 2),
            GridCell<LaneItem>(null, 3),
        ).inOrder()
    }

    @Test
    fun `lanes stop at the cap however tall the week`() {
        assertThat(lanesFitting(1000.dp, fontScale = 1f)).isEqualTo(LaneFit(MAX_LANES))
    }

    @Test
    fun `a week with no room for a lane draws no overflow row either`() {
        val layouts = fitWeeks(busyWeeks(perDay = 5), LaneFit(0))
        assertThat(layouts.none { it.showsOverflow }).isTrue()
        assertThat(layouts.all { it.lanes.isEmpty() }).isTrue()
    }

    @Test
    fun `folded events are counted in the overflow`() {
        val layout = WeekLayout(busyWeeks(perDay = 5)[1], laneCap = 1)
        assertThat(layout.usedLanes).isEqualTo(1)
        assertThat(layout.overflow).containsExactly(GridCell(4, 1), GridCell(4, 1), GridCell(4, 1),
            GridCell(4, 1), GridCell(4, 1), GridCell(4, 1), GridCell(4, 1)).inOrder()
    }

    @Test
    fun `a multi-day bar is one cell as wide as its days`() {
        val bar = allDayEvent(monday.plus(1, DateTimeUnit.DAY), monday.plus(3, DateTimeUnit.DAY), id = 1)
        val layout = WeekLayout(weeks(listOf(bar))[1], laneCap = 3)
        assertThat(layout.lanes).hasSize(1)
        assertThat(layout.lanes[0]).containsExactly(GridCell(null, 1), GridCell(bar, 3), GridCell(null, 3)).inOrder()
    }

    @Test
    fun `a bar on a folded lane moves into the overflow of each day it covers`() {
        val top = allDayEvent(monday, monday.plus(6, DateTimeUnit.DAY), id = 1)
        val lower = allDayEvent(monday.plus(2, DateTimeUnit.DAY), monday.plus(3, DateTimeUnit.DAY), id = 2)
        val layout = WeekLayout(weeks(listOf(top, lower))[1], laneCap = 1)
        assertThat(layout.lanes).hasSize(1)
        assertThat(layout.lanes[0]).containsExactly(GridCell(top, 7))
        assertThat(layout.overflow).containsExactly(GridCell(null, 2), GridCell(1, 1), GridCell(1, 1), GridCell(null, 3))
            .inOrder()
    }

    @Test
    fun `gaps are merged`() {
        val counts = listOf(0, 0, 2, 0, 0, 0, 1)
        val cells = mergeGaps(counts.size) { col -> counts[col].takeIf { it > 0 }?.let { GridCell(it, 1) } }
        assertThat(cells).containsExactly(GridCell(null, 2), GridCell(2, 1), GridCell(null, 3), GridCell(1, 1))
            .inOrder()
    }
}
