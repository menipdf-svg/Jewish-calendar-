package de.jeanlucmakiola.calendula.widget.upcoming

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

class UpcomingEventsTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun event(id: Long, start: Instant, end: Instant) = EventInstance(
        instanceId = id, eventId = id, calendarId = 1, title = "e$id",
        start = start, end = end, isAllDay = false, color = 0, location = null,
    )

    private fun data(vararg days: AgendaDay) = DaysWidgetData.Ready(
        today = LocalDate(2026, 10, 21),
        firstDay = LocalDate(2026, 10, 21),
        lastDay = LocalDate(2026, 10, 27),
        days = days.toList(),
        weekStart = DayOfWeek.MONDAY,
        is24Hour = true,
        soften = true,
        options = AgendaWidgetOptions(AgendaRange.Week, WidgetSize.SMALL, true, PastEventDisplay.SHOW),
        now = now,
    )

    @Test
    fun `ended events drop out and a multi-day one lists once, on its first day`() {
        val ended = event(1, now - 3.hours, now - 1.hours)
        val running = event(2, now - 1.hours, now + 30.hours)
        val later = event(3, now + 2.hours, now + 3.hours)
        val today = AgendaDay(LocalDate(2026, 10, 21), listOf(ended, running, later))
        val tomorrow = AgendaDay(LocalDate(2026, 10, 22), listOf(running))

        val upcoming = upcomingEvents(data(today, tomorrow))

        assertThat(upcoming.map { it.second.instanceId }).containsExactly(2L, 3L).inOrder()
        assertThat(upcoming.first().first).isEqualTo(LocalDate(2026, 10, 21))
    }
}
