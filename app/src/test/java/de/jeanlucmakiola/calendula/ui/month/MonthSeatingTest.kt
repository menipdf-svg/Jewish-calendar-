package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertWithMessage
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

/**
 * The month row's seating — which event sits in which lane of a day, and which
 * fall to the overflow — is read in four places: the bars and pills a row draws,
 * the split style's dots ([laneEvents]), the hit test ([chipAt]) and the overflow
 * ([overflowEvents]). They must agree for dot _i_ to morph into lane _i_ (#53).
 *
 * The references below are the four copies as they were before they shared one
 * implementation, the row's taken from its composable; this pins the shared one
 * to all of them across a spread of generated weeks.
 */
class MonthSeatingTest {

    private val zone = TimeZone.of("Europe/Berlin")
    private val firstMonday = LocalDate(2026, 3, 23)

    private var overflowed = 0
    private var stackedBars = 0

    @Test
    fun `every reader of a week sees the same seating`() {
        val random = Random(53)
        repeat(400) { round ->
            val start = firstMonday.plus(round % 9, DateTimeUnit.WEEK)
            val days = (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
            val events = randomEvents(random, start, count = random.nextInt(0, 14))
            val laid = layoutCalendarWeek(days, events, zone)
            val weeks = listOf(laid, clipWeekToMonth(laid, YearMonth(days[3].year, days[3].month)))
            weeks.forEach { week ->
                for (laneCap in 0..6) checkWeek(week, laneCap, "round $round, laneCap $laneCap")
                if (week.spans.any { it.lane > 0 }) stackedBars++
            }
        }
        assertWithMessage("generated days with overflow").that(overflowed).isGreaterThan(500)
        assertWithMessage("generated weeks with stacked bars").that(stackedBars).isGreaterThan(100)
    }

    private fun checkWeek(week: MonthWeek, laneCap: Int, label: String) {
        week.days.forEachIndexed { col, day ->
            val where = "$label, col $col"
            val row = rowSeating(week, col, day, laneCap)
            val dots = refLaneEvents(week, col, day, laneCap)
            val hits = (0 until laneCap).mapNotNull { lane -> refChipAt(week, col, lane, laneCap)?.let { lane to it } }
            val overflow = refOverflowEvents(week, col, day, laneCap)
            if (overflow.isNotEmpty()) overflowed++

            assertWithMessage("$where: hit test vs row").that(hits).isEqualTo(row.seated)
            assertWithMessage("$where: dots vs row").that(dots).isEqualTo(row.seated.map { it.second })
            assertWithMessage("$where: overflow vs row").that(overflow).isEqualTo(row.hiddenEvents)
            assertWithMessage("$where: dot count vs hidden list")
                .that(row.hiddenCount.coerceAtLeast(0)).isEqualTo(row.hiddenEvents.size)

            assertWithMessage("$where: laneEvents").that(week.laneEvents(col, laneCap)).isEqualTo(dots)
            assertWithMessage("$where: overflowEvents").that(week.overflowEvents(col, laneCap)).isEqualTo(overflow)
            (-1..laneCap).forEach { lane ->
                assertWithMessage("$where: chipAt lane $lane")
                    .that(week.chipAt(col, lane, laneCap))
                    .isEqualTo(refChipAt(week, col, lane, laneCap))
            }
            val seating = week.seating(col, laneCap)
            assertWithMessage("$where: seating lanes")
                .that(seating.seated.map { it.lane to it.event }).isEqualTo(row.seated)
            assertWithMessage("$where: seating hidden").that(seating.hidden).isEqualTo(row.hiddenEvents)
        }
    }

    private fun randomEvents(random: Random, weekStart: LocalDate, count: Int): List<EventInstance> =
        (1..count).map { n ->
            val id = n.toLong()
            val day = weekStart.plus(random.nextInt(-4, 10), DateTimeUnit.DAY)
            val response = if (random.nextInt(6) == 0) EventResponse.Declined else EventResponse.Going
            when (random.nextInt(5)) {
                0, 1 -> {
                    val from = day.atTime(0, 0).toInstant(TimeZone.UTC)
                    val to = day.plus(random.nextInt(1, 10), DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC)
                    testEvent(id = id, title = "A$id", start = from, end = to, isAllDay = true, response = response)
                }
                2 -> {
                    // Crosses midnight, sometimes ending on it exactly.
                    val from = day.atTime(random.nextInt(18, 24), 0).toInstant(zone)
                    val to = from + (random.nextInt(1, 60) * 30).minutes
                    testEvent(id = id, title = "M$id", start = from, end = to, response = response)
                }
                else -> {
                    val from = day.atTime(random.nextInt(0, 23), random.nextInt(0, 4) * 15).toInstant(zone)
                    val length = if (random.nextInt(8) == 0) 0 else random.nextInt(1, 8) * 15
                    testEvent(id = id, title = "T$id", start = from, end = from + length.minutes, response = response)
                }
            }
        }

    private class RowSeating(
        val seated: List<Pair<Int, EventInstance>>,
        val hiddenEvents: List<EventInstance>,
        val hiddenCount: Int,
    )

    /** MonthWeekRow's bars, pills and overflow dots, as the composable worked them out. */
    private fun rowSeating(week: MonthWeek, col: Int, d: LocalDate, laneCap: Int): RowSeating {
        val laneCount = (week.spans.maxOfOrNull { it.lane } ?: -1) + 1
        val shownLanes = laneCount.coerceAtMost(laneCap)
        val bars = week.spans
            .filter { it.lane < shownLanes && col in it.startCol..it.endCol }
            .map { it.lane to it.event }
        val timed = week.timedByDay[d].orEmpty()
        val occupied = week.spans
            .filter { it.lane < shownLanes && col in it.startCol..it.endCol }
            .map { it.lane }
            .toSet()
        val freeSlots = (0 until laneCap).filter { it !in occupied }
        val pillsShown = timed.take(freeSlots.size)
        val pills = pillsShown.mapIndexed { i, ev -> freeSlots[i] to ev }
        val hidden = (week.countByDay[d] ?: 0) - occupied.size - pillsShown.size
        val hiddenEvents = buildList {
            week.spans
                .filter { it.lane >= shownLanes && col in it.startCol..it.endCol }
                .forEach { add(it.event) }
            addAll(timed.drop(pillsShown.size))
        }
        return RowSeating((bars + pills).sortedBy { it.first }, hiddenEvents, hidden)
    }

    private fun refLaneEvents(week: MonthWeek, col: Int, day: LocalDate, laneCap: Int): List<EventInstance> {
        val byLane = arrayOfNulls<EventInstance>(laneCap)
        week.spans.forEach { span ->
            if (span.lane < laneCap && col in span.startCol..span.endCol) byLane[span.lane] = span.event
        }
        val free = (0 until laneCap).filter { byLane[it] == null }
        week.timedByDay[day].orEmpty().take(free.size).forEachIndexed { i, event -> byLane[free[i]] = event }
        return byLane.filterNotNull()
    }

    private fun refChipAt(week: MonthWeek, col: Int, lane: Int, laneCap: Int): EventInstance? {
        if (col !in week.days.indices || lane !in 0 until laneCap) return null
        week.spans.firstOrNull { it.lane == lane && col in it.startCol..it.endCol }?.let { return it.event }
        val occupied = week.spans
            .filter { it.lane < laneCap && col in it.startCol..it.endCol }
            .map { it.lane }
            .toSet()
        val free = (0 until laneCap).filter { it !in occupied }
        val index = free.indexOf(lane).takeIf { it >= 0 } ?: return null
        return week.timedByDay[week.days[col]].orEmpty().take(free.size).getOrNull(index)
    }

    private fun refOverflowEvents(week: MonthWeek, col: Int, day: LocalDate, laneCap: Int): List<EventInstance> {
        val seatedLanes = week.spans.count { it.lane < laneCap && col in it.startCol..it.endCol }
        return buildList {
            week.spans.forEach { span ->
                if (span.lane >= laneCap && col in span.startCol..span.endCol) add(span.event)
            }
            addAll(week.timedByDay[day].orEmpty().drop(laneCap - seatedLanes))
        }
    }
}
