package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class EventDisplayOrderTest {

    @Test
    fun `whole-day events order by title east and west of UTC`() {
        listOf("Europe/Berlin", "America/New_York").map(TimeZone::of).forEach { zone ->
            val day = LocalDate(2026, 11, 5)
            val allDay = testEvent(
                id = 1, title = "B all-day", isAllDay = true,
                start = Instant.parse("2026-11-05T00:00:00Z"), end = Instant.parse("2026-11-06T00:00:00Z"),
            )
            val timed = testEvent(
                id = 2, title = "A timed",
                start = day.atStartOfDayIn(zone), end = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone),
            )

            assertThat(listOf(allDay, timed).sortedForDisplay(zone)).containsExactly(timed, allDay).inOrder()
            assertThat(listOf(timed, allDay).sortedForDisplay(zone)).containsExactly(timed, allDay).inOrder()
        }
    }
}
