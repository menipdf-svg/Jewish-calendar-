package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.widget.WidgetSize
import org.junit.jupiter.api.Test

class AgendaScaleTest {

    // --- the "default size is unchanged" regression guard ---------------------

    @Test
    fun `SMALL metrics equal the widget's original constants`() {
        // SMALL is the default, so if this fails an agenda widget whose owner never
        // touched the size setting no longer looks as it did (#51).
        val m = metricsFor(WidgetSize.SMALL)
        assertThat(m.eventTitle).isEqualTo(14.sp)
        assertThat(m.eventTime).isEqualTo(12.sp)
        assertThat(m.placeholder).isEqualTo(14.sp)
        assertThat(m.message).isEqualTo(14.sp)
        assertThat(m.contentH).isEqualTo(36.dp)
        assertThat(m.rowVPad).isEqualTo(4.dp)
    }

    // --- the ramp ------------------------------------------------------------

    @Test
    fun `sizes are non-decreasing across the steps`() {
        val steps = WidgetSize.entries.map(::metricsFor)

        steps.zipWithNext { small, big ->
            assertThat(big.pillWeekday.value).isAtLeast(small.pillWeekday.value)
            assertThat(big.pillDay.value).isAtLeast(small.pillDay.value)
            assertThat(big.eventTitle.value).isAtLeast(small.eventTitle.value)
            assertThat(big.eventTime.value).isAtLeast(small.eventTime.value)
            assertThat(big.placeholder.value).isAtLeast(small.placeholder.value)
            assertThat(big.message.value).isAtLeast(small.message.value)
            assertThat(big.contentH.value).isAtLeast(small.contentH.value)
            assertThat(big.pillWidth.value).isAtLeast(small.pillWidth.value)
            assertThat(big.rowVPad.value).isAtLeast(small.rowVPad.value)
            assertThat(big.dayGap.value).isAtLeast(small.dayGap.value)
            assertThat(big.monthLabel.value).isAtLeast(small.monthLabel.value)
        }
    }

    @Test
    fun `the event title keeps its lead over the time line`() {
        // The secondary line steps more slowly on purpose; if it ever caught up the
        // row would lose its hierarchy.
        WidgetSize.entries.map(::metricsFor).forEach { m ->
            assertThat(m.eventTitle.value).isGreaterThan(m.eventTime.value)
        }
    }

    @Test
    fun `no step grows type more than half again over the baseline`() {
        // Guards against a future edit turning "more readable" into "absurd".
        val base = metricsFor(WidgetSize.SMALL)
        val top = metricsFor(WidgetSize.EXTRA_LARGE)
        assertThat(top.pillDay.value / base.pillDay.value).isLessThan(1.5f)
        assertThat(top.eventTitle.value / base.eventTitle.value).isLessThan(1.5f)
    }

    // --- font scale ----------------------------------------------------------

    @Test
    fun `the row height tracks the system font scale`() {
        // The row height is Dp, the text in it is sp: without this the two diverge
        // at large accessibility font settings and the text outgrows the row.
        val m = metricsFor(WidgetSize.SMALL)
        assertThat(m.scaledForFont(1f).contentH).isEqualTo(36.dp)
        assertThat(m.scaledForFont(1.3f).contentH.value).isWithin(0.01f).of(46.8f)
        assertThat(m.scaledForFont(0.85f).contentH.value).isWithin(0.01f).of(30.6f)
    }

    @Test
    fun `the date pill widens with the system font scale`() {
        // The pill's text is sp; a fixed-width pill clips "Wed" / "23" at large font settings.
        val m = metricsFor(WidgetSize.SMALL)
        assertThat(m.scaledForFont(2f).pillWidth).isEqualTo(m.pillWidth * 2)
        assertThat(m.scaledForFont(2f).rowHeight).isEqualTo(m.contentH * 2 + m.rowVPad * 2)
    }

    @Test
    fun `scaling for the default font scale changes nothing`() {
        val m = metricsFor(WidgetSize.LARGE)
        assertThat(m.scaledForFont(1f)).isSameInstanceAs(m)
    }

    @Test
    fun `font scaling leaves the sp sizes alone`() {
        // Glance already applies the font scale to sp; scaling them here too would
        // double-count it.
        val m = metricsFor(WidgetSize.MEDIUM)
        val scaled = m.scaledForFont(1.3f)
        assertThat(scaled.pillDay).isEqualTo(m.pillDay)
        assertThat(scaled.eventTitle).isEqualTo(m.eventTitle)
        assertThat(scaled.rowVPad).isEqualTo(m.rowVPad)
    }
}
