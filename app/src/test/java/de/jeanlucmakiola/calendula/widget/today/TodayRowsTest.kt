package de.jeanlucmakiola.calendula.widget.today

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDay
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.widget.AgendaWidgetOptions
import de.jeanlucmakiola.calendula.widget.DaysWidgetData
import de.jeanlucmakiola.calendula.widget.WidgetSize
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TodayRowsTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val today = LocalDate(2026, 10, 21)
    private val tomorrow = LocalDate(2026, 10, 22)

    private fun event(id: Long, start: Instant, end: Instant) = EventInstance(
        instanceId = id, eventId = id, calendarId = 1, title = "e$id",
        start = start, end = end, isAllDay = false, color = 0, location = null,
    )

    private fun data(past: PastEventDisplay, vararg days: AgendaDay) = DaysWidgetData.Ready(
        today = today,
        firstDay = today,
        lastDay = tomorrow,
        days = days.toList(),
        weekStart = DayOfWeek.MONDAY,
        is24Hour = true,
        soften = true,
        options = AgendaWidgetOptions(AgendaRange.Day, WidgetSize.SMALL, true, past),
        now = now,
    )

    private val ended = event(1, now - 3.hours, now - 1.hours)
    private val later = event(2, now + 1.hours, now + 2.hours)
    private val next = event(3, now + 22.hours, now + 23.hours)

    @Test
    fun `an event still to come lists today, finished ones as the past setting says`() {
        val days = arrayOf(AgendaDay(today, listOf(ended, later)), AgendaDay(tomorrow, listOf(next)))
        assertThat(todayRows(data(PastEventDisplay.DIM, *days))).isEqualTo(TodayRows.Today(listOf(ended, later)))
        assertThat(todayRows(data(PastEventDisplay.HIDE, *days))).isEqualTo(TodayRows.Today(listOf(later)))
    }

    @Test
    fun `once today is over it looks ahead to tomorrow`() {
        val over = data(PastEventDisplay.SHOW, AgendaDay(today, listOf(ended)), AgendaDay(tomorrow, listOf(next)))
        assertThat(todayRows(over)).isEqualTo(TodayRows.NothingLeft(listOf(next)))
        assertThat(todayRows(data(PastEventDisplay.SHOW))).isEqualTo(TodayRows.NothingLeft(emptyList()))
    }
}
