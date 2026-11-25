package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.coversWholeDays
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.calendula.ui.common.inlineTimeLabel
import de.jeanlucmakiola.calendula.ui.common.rememberWidestTimeSample
import de.jeanlucmakiola.calendula.ui.common.trimmedLines
import kotlinx.datetime.TimeZone
import java.util.Locale

/**
 * The start time a chip shows before its title (#219), or null when it has none
 * to show: a chip filling whole days never carries a time (#296), and neither
 * does a bar carried in from an earlier week, whose start is not in this segment.
 */
internal fun monthChipTime(
    event: EventInstance,
    continuesLeft: Boolean,
    zone: TimeZone,
    is24Hour: Boolean,
    locale: Locale,
): String? {
    if (event.coversWholeDays(zone) || continuesLeft) return null
    return formatTimeOfDay(event.start, zone, is24Hour, locale)
}

/** How many characters of a longer title a chip must keep beside its start time (#476). */
internal const val MONTH_TITLE_MIN_CHARS = 12

/**
 * The part of [title] that has to stay readable beside a start time: all of it,
 * or its first [MONTH_TITLE_MIN_CHARS] characters, counted in code points so an
 * emoji is never cut in half.
 */
internal fun monthTitleToKeep(title: String): String {
    if (title.codePointCount(0, title.length) <= MONTH_TITLE_MIN_CHARS) return title
    return title.substring(0, title.offsetByCodePoints(0, MONTH_TITLE_MIN_CHARS))
}

/**
 * Whether a chip shows its start time (#219, #476): only with the setting
 * [enabled], and only when [availableWidth] (the chip less its chrome) holds
 * [labelWidth], the widest time followed by [monthTitleToKeep] of the title.
 */
internal fun monthChipShowsTime(
    enabled: Boolean,
    availableWidth: Float,
    labelWidth: Float,
): Boolean = enabled && labelWidth <= availableWidth

/**
 * Prices month chips' time labels for [monthChipShowsTime] in the chips' own
 * text style. One per grid; each title is measured once for its lifetime.
 */
@Stable
internal class MonthChipTimeFit(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val density: Density,
    private val timeSample: String,
) {
    private val chromePx = with(density) { MONTH_CHIP_CHROME.toPx() }
    private val labelWidths = HashMap<String, Int>()

    fun showsTime(chipWidth: Dp, title: String): Boolean {
        if (labelWidths.size >= MAX_CACHED_TITLES) labelWidths.clear()
        val labelWidth = labelWidths.getOrPut(title) {
            val label = inlineTimeLabel(timeSample, monthTitleToKeep(title), Color.Unspecified)
            measurer.measure(label, style, softWrap = false, maxLines = 1).size.width
        }
        return monthChipShowsTime(
            enabled = true,
            availableWidth = with(density) { chipWidth.toPx() } - chromePx,
            labelWidth = labelWidth.toFloat(),
        )
    }

    private companion object {
        const val MAX_CACHED_TITLES = 1000
    }
}

/** The grid's [MonthChipTimeFit], or null when start times are switched off (#476). */
@Composable
internal fun rememberMonthChipTimeFit(showStartTimes: Boolean): MonthChipTimeFit? {
    if (!showStartTimes) return null
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val style = MaterialTheme.typography.labelSmall.trimmedLines()
    val density = LocalDensity.current
    val timeSample = rememberWidestTimeSample()
    return remember(measurer, style, density, timeSample) {
        MonthChipTimeFit(measurer, style, density, timeSample)
    }
}
