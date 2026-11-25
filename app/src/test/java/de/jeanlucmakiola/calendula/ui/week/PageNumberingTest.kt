package de.jeanlucmakiola.calendula.ui.week

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test

/**
 * Pins each grid's page numbering: a pager restores its page by number, so a
 * change here moves the user to another date. Page 0 holds the first day of
 * the shared span, and the last page holds its last day (#457).
 */
class PageNumberingTest {

    private val dates: List<LocalDate> = buildList {
        var d = LocalDate(1500, 1, 1)
        while (d < LocalDate(2500, 1, 1)) {
            add(d)
            d = d.plus(37, DateTimeUnit.DAY)
        }
        var e = LocalDate(1999, 12, 1)
        while (e < LocalDate(2000, 2, 1)) {
            add(e)
            e = e.plus(1, DateTimeUnit.DAY)
        }
        addAll(listOf(FIRST, FIRST.minus(1, DateTimeUnit.DAY), LAST, LAST.plus(1, DateTimeUnit.DAY)))
    }

    private fun pages(count: Int): List<Int> =
        listOf(-7, -1, 0, 1, count / 2 - 3, count / 2 - 1, count / 2, count / 2 + 1, count - 2, count - 1, count, count + 5) +
            (0 until count step 997)

    @Test
    fun `week pages keep their numbering for every first day`() {
        DayOfWeek.entries.forEach { firstDay ->
            val grid = DaysGrid.weeks(firstDay)
            val phase = DAYS_EPOCH.daysUntil(WEEK_EPOCH.weekStart(firstDay))
            assertThat(grid.dayCount).isEqualTo(7)
            assertThat(grid.pageCount).isEqualTo(expectedCount(7, phase))
            pages(grid.pageCount).forEach { page ->
                assertThat(grid.startForPage(page)).isEqualTo(expectedStart(page, 7, phase))
            }
            dates.forEach { date ->
                assertThat(grid.pageFor(date)).isEqualTo(expectedPage(date, 7, phase))
            }
        }
    }

    @Test
    fun `multi-day pages keep their numbering for every count and phase`() {
        (1..7).forEach { count ->
            (-count..count * 2).forEach { phase ->
                val grid = DaysGrid(count, phase)
                assertThat(grid.pageCount).isEqualTo(expectedCount(count, phase))
                pages(grid.pageCount).forEach { page ->
                    assertThat(grid.startForPage(page)).isEqualTo(expectedStart(page, count, phase))
                }
                dates.forEach { date ->
                    assertThat(grid.pageFor(date)).isEqualTo(expectedPage(date, count, phase))
                }
            }
            dates.filter { it.year in 1950..2050 }.forEach { date ->
                val grid = DaysGrid.startingAt(date, count)
                assertThat(grid.startForPage(grid.pageFor(date))).isEqualTo(date)
            }
        }
    }

    @Test
    fun `day pages keep their numbering`() {
        val grid = DaysGrid.days
        assertThat(grid.dayCount).isEqualTo(1)
        assertThat(grid.pageCount).isEqualTo(FIRST.daysUntil(LAST) + 1)
        pages(grid.pageCount).forEach { page ->
            assertThat(grid.startForPage(page)).isEqualTo(FIRST.plus(page, DateTimeUnit.DAY))
        }
        dates.forEach { date ->
            assertThat(grid.pageFor(date)).isEqualTo(FIRST.daysUntil(date).coerceIn(0, grid.pageCount - 1))
        }
    }

    @Test
    fun `every grid reaches both ends of the span`() {
        (DayOfWeek.entries.map { DaysGrid.weeks(it) } + DaysGrid.days + (1..7).map { DaysGrid(it, 0) }).forEach { grid ->
            assertThat(grid.startForPage(grid.pageFor(FIRST))).isAtMost(FIRST)
            val lastStart = grid.startForPage(grid.pageFor(LAST))
            assertThat(lastStart.plus(grid.dayCount - 1, DateTimeUnit.DAY)).isAtLeast(LAST)
        }
    }

    @Test
    fun `a multi-day jump re-frames exactly when the date would open mid-page`() {
        val inRange = dates.filter { it.year in 1950..2050 }
        (3..6).forEach { count ->
            val grid = DaysGrid.startingAt(LocalDate(2026, 10, 16), count)
            inRange.forEach { target ->
                val midPage = grid.startForPage(grid.pageFor(target)) != target
                assertThat(DaysGrid.startingAt(target, count) != grid).isEqualTo(midPage)
            }
        }
    }

    @Test
    fun `grids are equal exactly when they page the same way`() {
        assertThat(DaysGrid.weeks(DayOfWeek.MONDAY)).isEqualTo(DaysGrid.weeks(DayOfWeek.MONDAY))
        assertThat(DaysGrid.weeks(DayOfWeek.MONDAY)).isNotEqualTo(DaysGrid.weeks(DayOfWeek.SUNDAY))
        assertThat(DaysGrid(3, 1)).isEqualTo(DaysGrid(3, 1))
        assertThat(DaysGrid(3, 1)).isNotEqualTo(DaysGrid(3, 2))
        assertThat(DaysGrid(3, 1)).isNotEqualTo(DaysGrid(4, 1))
        assertThat(DaysGrid.days).isEqualTo(DaysGrid.days)
        assertThat(DaysGrid.days.hashCode()).isEqualTo(DaysGrid.days.hashCode())
    }

    private companion object {
        val FIRST = LocalDate(1600, 1, 1)
        val LAST = LocalDate(2399, 12, 31)
        val WEEK_EPOCH = LocalDate(2000, 1, 3)
        val DAYS_EPOCH = LocalDate(2000, 1, 1)

        fun LocalDate.weekStart(firstDay: DayOfWeek): LocalDate =
            minus(((dayOfWeek.ordinal - firstDay.ordinal) + 7) % 7, DateTimeUnit.DAY)

        /** Page 0 starts on the page boundary at or before [FIRST]. */
        fun firstStart(count: Int, phase: Int): LocalDate {
            val origin = DAYS_EPOCH.plus(phase, DateTimeUnit.DAY)
            return origin.plus(Math.floorDiv(origin.daysUntil(FIRST), count) * count, DateTimeUnit.DAY)
        }

        fun expectedStart(page: Int, count: Int, phase: Int): LocalDate =
            firstStart(count, phase).plus(page * count, DateTimeUnit.DAY)

        fun expectedCount(count: Int, phase: Int): Int =
            Math.floorDiv(firstStart(count, phase).daysUntil(LAST), count) + 1

        fun expectedPage(date: LocalDate, count: Int, phase: Int): Int =
            Math.floorDiv(firstStart(count, phase).daysUntil(date), count)
                .coerceIn(0, expectedCount(count, phase) - 1)
    }
}
