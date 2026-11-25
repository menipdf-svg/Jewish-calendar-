package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class IsPastOnTest {

    private val zone = TimeZone.of("Europe/Berlin")
    private val today = LocalDate(2026, 10, 8)
    private val now = Instant.parse("2026-10-08T12:00:00Z")

    // All-day Oct 6–9, still running today.
    private val summit = testEvent(
        title = "Summit",
        start = Instant.parse("2026-10-06T00:00:00Z"),
        end = Instant.parse("2026-10-10T00:00:00Z"),
        isAllDay = true,
    )

    @Test
    fun `an ongoing multi-day event is past on the days already behind`() {
        assertThat(summit.isPastOn(LocalDate(2026, 10, 7), now, zone)).isTrue()
    }

    @Test
    fun `an ongoing multi-day event is not past today or later`() {
        assertThat(summit.isPastOn(today, now, zone)).isFalse()
        assertThat(summit.isPastOn(LocalDate(2026, 10, 9), now, zone)).isFalse()
    }

    @Test
    fun `an event that ended earlier today is past`() {
        val standup = summit.copy(
            isAllDay = false,
            start = Instant.parse("2026-10-08T07:00:00Z"),
            end = Instant.parse("2026-10-08T07:15:00Z"),
        )
        assertThat(standup.isPastOn(today, now, zone)).isTrue()
    }
}
