package de.jeanlucmakiola.calendula.widget

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MonthWindowCenterTest {

    @Test
    fun `the loaded window always reaches two months past the shown one`() {
        for (index in -40..40) {
            val center = monthWindowCenter(index)
            assertThat(index - 2).isAtLeast(center - MONTH_WIDGET_PAD_MONTHS)
            assertThat(index + 2).isAtMost(center + MONTH_WIDGET_PAD_MONTHS)
        }
    }

    @Test
    fun `paging within a step of three keeps the same window`() {
        val year = 2026 * 12
        assertThat(monthWindowCenter(year)).isEqualTo(monthWindowCenter(year + 2))
        assertThat(monthWindowCenter(year + 3)).isNotEqualTo(monthWindowCenter(year + 2))
    }
}
