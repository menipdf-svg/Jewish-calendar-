package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** Covers the agenda widget's "always show today" anchor logic (#35). */
class AnchorTodayTest {

    private val today = LocalDate(2026, 6, 17)
    private val tomorrow = LocalDate(2026, 6, 18)
    private val yesterday = LocalDate(2026, 6, 16)

    @Test
    fun `disabled leaves the days untouched even when today is absent`() {
        val days = listOf(AgendaDay(tomorrow, emptyList()))
        assertThat(anchorTodayIfMissing(days, today, enabled = false)).isEqualTo(days)
    }

    @Test
    fun `enabled prepends an empty today when it is missing`() {
        val days = listOf(AgendaDay(tomorrow, emptyList()))
        val result = anchorTodayIfMissing(days, today, enabled = true)
        assertThat(result).hasSize(2)
        assertThat(result.first()).isEqualTo(AgendaDay(today, emptyList()))
        assertThat(result[1]).isEqualTo(days.first())
    }

    @Test
    fun `enabled anchors today into an otherwise empty list`() {
        val result = anchorTodayIfMissing(emptyList(), today, enabled = true)
        assertThat(result).containsExactly(AgendaDay(today, emptyList()))
    }

    @Test
    fun `enabled is a no-op when today already has its own day`() {
        val days = listOf(AgendaDay(today, emptyList()), AgendaDay(tomorrow, emptyList()))
        assertThat(anchorTodayIfMissing(days, today, enabled = true)).isEqualTo(days)
    }

    @Test
    fun `enabled slots today in after loaded past days`() {
        val days = listOf(AgendaDay(yesterday, emptyList()), AgendaDay(tomorrow, emptyList()))
        val result = anchorTodayIfMissing(days, today, enabled = true)
        assertThat(result.map { it.date }).containsExactly(yesterday, today, tomorrow).inOrder()
    }

    @Test
    fun `enabled appends today after only past days`() {
        val days = listOf(AgendaDay(yesterday, emptyList()))
        val result = anchorTodayIfMissing(days, today, enabled = true)
        assertThat(result.map { it.date }).containsExactly(yesterday, today).inOrder()
    }
}
