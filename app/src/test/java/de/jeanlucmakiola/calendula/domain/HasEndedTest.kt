package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.testEvent
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class HasEndedTest {

    private val newYork = TimeZone.of("America/New_York")

    // All-day Oct 28, stored as UTC midnights.
    private val holiday = testEvent(
        title = "Holiday",
        start = Instant.parse("2026-10-28T00:00:00Z"),
        end = Instant.parse("2026-10-29T00:00:00Z"),
        isAllDay = true,
    )

    @Test
    fun `an all-day event runs until local midnight west of UTC`() {
        // 20:00 in New York, past the stored UTC end.
        assertThat(holiday.hasEnded(Instant.parse("2026-10-29T00:00:00Z"), newYork)).isFalse()
        assertThat(holiday.hasEnded(Instant.parse("2026-10-29T04:00:00Z"), newYork)).isTrue()
    }

    @Test
    fun `an all-day event ends at local midnight east of UTC`() {
        val berlin = TimeZone.of("Europe/Berlin")
        assertThat(holiday.hasEnded(Instant.parse("2026-10-28T23:30:00Z"), berlin)).isTrue()
    }

    @Test
    fun `a timed event ends at its own end`() {
        val call = holiday.copy(isAllDay = false, end = Instant.parse("2026-10-28T15:00:00Z"))
        assertThat(call.hasEnded(Instant.parse("2026-10-28T15:00:00Z"), newYork)).isTrue()
        assertThat(call.hasEnded(Instant.parse("2026-10-28T14:59:00Z"), newYork)).isFalse()
    }
}
