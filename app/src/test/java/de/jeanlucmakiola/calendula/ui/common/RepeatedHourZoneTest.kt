package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.time.Instant

/** The zone named after a time only where the fall-back hour repeats it (#387). */
class RepeatedHourZoneTest {

    private val brussels = TimeZone.of("Europe/Brussels")

    @Test
    fun `both 02 30s of the fall-back day are told apart`() {
        val first = withRepeatedHourZone("2:30 AM", Instant.parse("2026-10-25T00:30:00Z"), brussels, Locale.UK)
        val second = withRepeatedHourZone("2:30 AM", Instant.parse("2026-10-25T01:30:00Z"), brussels, Locale.UK)
        assertThat(first).startsWith("2:30 AM ")
        assertThat(second).startsWith("2:30 AM ")
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `an unambiguous time stays bare`() {
        assertThat(withRepeatedHourZone("2:45 PM", Instant.parse("2026-10-25T13:45:00Z"), brussels, Locale.UK))
            .isEqualTo("2:45 PM")
        assertThat(withRepeatedHourZone("3:30 AM", Instant.parse("2026-10-25T02:30:00Z"), brussels, Locale.UK))
            .isEqualTo("3:30 AM")
    }
}
