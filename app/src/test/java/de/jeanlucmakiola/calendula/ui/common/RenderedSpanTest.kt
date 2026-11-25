package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** A short block near midnight keeps its whole floor rather than running off the day (#483). */
class RenderedSpanTest {

    private val floor = MIN_EVENT_FRACTION * 60f

    @Test
    fun `a long block draws at its own times`() {
        assertThat(renderedSpan(540, 600, continuesAfter = false)).isEqualTo(RenderedSpan(540f, 600f))
    }

    @Test
    fun `a short block in the day is floored downwards`() {
        val span = renderedSpan(20 * 60 + 58, 20 * 60 + 59, continuesAfter = false)
        assertThat(span).isEqualTo(RenderedSpan(1258f, 1258f + floor))
    }

    @Test
    fun `a short block at the end of the day rises to end at midnight`() {
        val span = renderedSpan(23 * 60 + 58, 23 * 60 + 59, continuesAfter = false)
        assertThat(span).isEqualTo(RenderedSpan(1440f - floor, 1440f))
    }

    @Test
    fun `a block ending at midnight rises too`() {
        val span = renderedSpan(23 * 60 + 59, 1440, continuesAfter = false)
        assertThat(span).isEqualTo(RenderedSpan(1440f - floor, 1440f))
    }

    @Test
    fun `an instant event at the end of the day rises`() {
        val span = renderedSpan(1439, 1439, continuesAfter = false)
        assertThat(span.bottomMin).isEqualTo(1440f)
        assertThat(span.bottomMin - span.topMin).isEqualTo(floor)
    }

    @Test
    fun `a block that just fits stays put`() {
        val start = 1440 - 26
        assertThat(renderedSpan(start, start + 5, continuesAfter = false)).isEqualTo(RenderedSpan(start.toFloat(), 1440f))
    }

    @Test
    fun `a block carrying on past midnight keeps its start`() {
        val span = renderedSpan(23 * 60 + 58, 1440, continuesAfter = true)
        assertThat(span.topMin).isEqualTo(1438f)
    }
}
