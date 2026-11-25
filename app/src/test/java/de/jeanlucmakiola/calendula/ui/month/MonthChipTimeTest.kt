package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.allDayEvent
import de.jeanlucmakiola.calendula.timedEvent
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import java.util.Locale

/** Which month chips carry a start time, and how it reads (#219).
 *  How it is *set* against the title lives in `EventTimeStyleTest`. */
class MonthChipTimeTest {

    private val zone = TimeZone.UTC
    private val locale = Locale.UK
    private val day = LocalDate(2026, Month.SEPTEMBER, 2)

    private fun timed(hour: Int, minute: Int) =
        timedEvent(day, hour, zone = zone, title = "Standup", minute = minute)

    private fun allDay() = allDayEvent(day, id = 2L, title = "Holiday", zone = zone)

    @Test
    fun `a timed chip shows its start in the 24-hour convention`() {
        val time = monthChipTime(timed(9, 5), continuesLeft = false, zone, is24Hour = true, locale)
        assertThat(time).isEqualTo("09:05")
    }

    @Test
    fun `the 12-hour setting is honoured`() {
        val time = monthChipTime(timed(14, 30), continuesLeft = false, zone, is24Hour = false, locale)
        assertThat(time).isEqualTo("2:30 pm")
    }

    @Test
    fun `an all-day chip never shows a time`() {
        val time = monthChipTime(allDay(), continuesLeft = false, zone, is24Hour = true, locale)
        assertThat(time).isNull()
    }

    @Test
    fun `a bar carried in from the previous week shows none either`() {
        // Its start is not in this segment, so printing it would put a time on a
        // row the event does not begin on.
        val time = monthChipTime(timed(9, 5), continuesLeft = true, zone, is24Hour = true, locale)
        assertThat(time).isNull()
    }

    @Test
    fun `switched off, no chip shows a time however wide`() {
        assertThat(monthChipShowsTime(enabled = false, availableWidth = 1000f, labelWidth = 10f)).isFalse()
    }

    @Test
    fun `the time shows only while the label fits beside it`() {
        assertThat(monthChipShowsTime(enabled = true, availableWidth = 80f, labelWidth = 80f)).isTrue()
        assertThat(monthChipShowsTime(enabled = true, availableWidth = 79f, labelWidth = 80f)).isFalse()
    }

    @Test
    fun `a short title has to fit whole`() {
        assertThat(monthTitleToKeep("Lunch")).isEqualTo("Lunch")
        assertThat(monthTitleToKeep("Appointments")).isEqualTo("Appointments")
    }

    @Test
    fun `a long title only has to keep its first twelve characters`() {
        assertThat(monthTitleToKeep("Quarterly planning review")).isEqualTo("Quarterly pl")
    }

    @Test
    fun `an emoji at the cut is kept whole`() {
        val title = "Birthday 🎂 party at home"
        val kept = monthTitleToKeep(title)
        assertThat(kept).isEqualTo("Birthday 🎂 p")
        assertThat(Character.isHighSurrogate(kept.last())).isFalse()
    }

    @Test
    fun `an emoji as the twelfth character is not split`() {
        val kept = monthTitleToKeep("Team dinner🍕 tonight")
        assertThat(kept).isEqualTo("Team dinner🍕")
    }
}
