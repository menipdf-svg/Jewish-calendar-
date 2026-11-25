package de.jeanlucmakiola.calendula.widget

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class WeekOfTest {

    @Test
    fun `the week starts on the configured day and holds today`() {
        val wednesday = LocalDate(2026, 10, 21)
        assertThat(weekOf(wednesday, DayOfWeek.MONDAY)).isEqualTo(LocalDate(2026, 10, 19)..LocalDate(2026, 10, 25))
        assertThat(weekOf(wednesday, DayOfWeek.SUNDAY)).isEqualTo(LocalDate(2026, 10, 18)..LocalDate(2026, 10, 24))
        assertThat(weekOf(wednesday, DayOfWeek.WEDNESDAY).start).isEqualTo(wednesday)
        assertThat(weekOf(wednesday, DayOfWeek.THURSDAY).start).isEqualTo(LocalDate(2026, 10, 15))
    }
}
