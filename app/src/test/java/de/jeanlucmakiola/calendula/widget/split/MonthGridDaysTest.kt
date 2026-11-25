package de.jeanlucmakiola.calendula.widget.split

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class MonthGridDaysTest {

    @Test
    fun `whole weeks from the week start cover the month and no more`() {
        // October 2026 starts on a Thursday and has 31 days.
        val october = 2026 * 12 + 9
        val monday = monthGridDays(october, DayOfWeek.MONDAY)
        assertThat(monday.first()).isEqualTo(LocalDate(2026, 9, 28))
        assertThat(monday.last()).isEqualTo(LocalDate(2026, 11, 1))
        assertThat(monday).hasSize(35)

        // February 2027 starts on a Monday and fills exactly four weeks.
        val february = 2027 * 12 + 1
        assertThat(monthGridDays(february, DayOfWeek.MONDAY)).hasSize(28)
        assertThat(monthGridDays(february, DayOfWeek.SUNDAY)).hasSize(35)
    }
}
