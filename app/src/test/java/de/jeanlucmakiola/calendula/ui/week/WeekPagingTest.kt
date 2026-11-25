package de.jeanlucmakiola.calendula.ui.week

import de.jeanlucmakiola.calendula.ui.common.startOfWeek
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test

class WeekPagingTest {

    // 2026-09-27 is a Sunday.
    private val sunday = LocalDate(2026, 9, 27)

    @Test
    fun `a page and its week round-trip for every first day`() {
        DayOfWeek.entries.forEach { firstDay ->
            val page = DaysGrid.weeks(firstDay).pageFor(sunday)
            val start = DaysGrid.weeks(firstDay).startForPage(page)
            assertThat(start).isEqualTo(sunday.startOfWeek(firstDay))
            assertThat(DaysGrid.weeks(firstDay).pageFor(start)).isEqualTo(page)
        }
    }

    @Test
    fun `every day of a week maps to the same page`() {
        val start = sunday.startOfWeek(DayOfWeek.MONDAY)
        val pages = (0..6).map { DaysGrid.weeks(DayOfWeek.MONDAY).pageFor(start.plus(it, DateTimeUnit.DAY)) }
        assertThat(pages.toSet()).hasSize(1)
    }

    @Test
    fun `adjacent pages are adjacent weeks`() {
        val page = DaysGrid.weeks(DayOfWeek.SUNDAY).pageFor(sunday)
        assertThat(DaysGrid.weeks(DayOfWeek.SUNDAY).startForPage(page + 1))
            .isEqualTo(LocalDate(2026, 10, 4))
        assertThat(DaysGrid.weeks(DayOfWeek.SUNDAY).startForPage(page - 1))
            .isEqualTo(LocalDate(2026, 9, 20))
    }

    @Test
    fun `dates before the epoch page resolve too`() {
        val old = LocalDate(1990, 5, 16)
        val page = DaysGrid.weeks(DayOfWeek.MONDAY).pageFor(old)
        assertThat(DaysGrid.weeks(DayOfWeek.MONDAY).startForPage(page)).isEqualTo(LocalDate(1990, 5, 14))
    }

    @Test
    fun `multi-day pages begin on the selected date and advance by the chosen count`() {
        (3..6).forEach { count ->
            val grid = DaysGrid.startingAt(sunday, count)
            val page = grid.pageFor(sunday)
            assertThat(grid.startForPage(page)).isEqualTo(sunday)
            assertThat(grid.startForPage(page + 1)).isEqualTo(sunday.plus(count, DateTimeUnit.DAY))
            assertThat(grid.pageFor(sunday.plus(count - 1, DateTimeUnit.DAY))).isEqualTo(page)
        }
    }

    @Test
    fun `multi-day pages also work before the epoch`() {
        val date = LocalDate(1990, 5, 16)
        val grid = DaysGrid.startingAt(date, 3)
        val page = grid.pageFor(date)
        assertThat(grid.startForPage(page)).isEqualTo(date)
        assertThat(grid.startForPage(page - 1)).isEqualTo(LocalDate(1990, 5, 13))
    }

    @Test
    fun `a day page and its day round-trip`() {
        listOf(LocalDate(2026, 9, 27), LocalDate(1990, 5, 16), LocalDate(2100, 2, 28)).forEach { day ->
            assertThat(DaysGrid.days.startForPage(DaysGrid.days.pageFor(day))).isEqualTo(day)
        }
    }

    @Test
    fun `adjacent day pages are adjacent days across a month end`() {
        val page = DaysGrid.days.pageFor(LocalDate(2026, 2, 28))
        assertThat(DaysGrid.days.startForPage(page + 1)).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(DaysGrid.days.startForPage(page - 1)).isEqualTo(LocalDate(2026, 2, 27))
    }
}
