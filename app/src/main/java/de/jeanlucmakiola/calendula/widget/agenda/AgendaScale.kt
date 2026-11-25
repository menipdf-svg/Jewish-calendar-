package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.calendula.widget.WidgetSize

/** Horizontal inset of a row's text inside its card; doesn't scale, so it never eats title width. */
internal val ROW_H_PAD = 12.dp

/** Space between the date-pill gutter and a day's card. */
internal val GUTTER_GAP = 6.dp

/** The classic style's row inset, colour stripe and the gap after it (#489). */
internal val CLASSIC_ROW_H_PAD = 4.dp
internal val STRIPE_WIDTH = 5.dp
internal val STRIPE_GAP = 10.dp

/** The stripe and its gap, and where the classic style's event text starts, so its placeholder lines up with the titles. */
internal val STRIPE_INSET = STRIPE_WIDTH + STRIPE_GAP
internal val TEXT_INDENT = CLASSIC_ROW_H_PAD + STRIPE_INSET

/**
 * Every size the agenda widget varies by [WidgetSize]. Values that genuinely
 * shouldn't grow (the horizontal row constants above, corner radii) stay
 * constants rather than routing through here.
 */
internal data class AgendaMetrics(
    val pillWeekday: TextUnit,  // date pill weekday ("Thu")
    val pillDay: TextUnit,      // date pill day number
    val eventTitle: TextUnit,   // event title line
    val eventTime: TextUnit,    // time/location line
    val placeholder: TextUnit,  // "no more events today" (#35)
    val message: TextUnit,      // empty/permission centred message
    val contentH: Dp,           // an event row's two text lines
    val pillWidth: Dp,          // date pill / gutter width
    val rowVPad: Dp,            // event row vertical padding
    val dayGap: Dp,             // space between two days' cards
    val monthLabel: TextUnit,   // month name above a new month's first day
    val dayHeader: TextUnit,    // classic style's day label ("Today · …")
    val dayHeaderTopPad: Dp,    // classic style's space above a day label
) {
    /** One event row's height, shared by the date pill beside it and the placeholder row. */
    val rowHeight: Dp get() = contentH + rowVPad * 2

    /**
     * Resolves the row content height and pill width against the user's system font
     * scale: both are [Dp] while the text in and beside them is `sp`, so without
     * this the text outgrows them at large font settings.
     */
    fun scaledForFont(fontScale: Float): AgendaMetrics =
        if (fontScale == 1f) this else copy(contentH = contentH * fontScale, pillWidth = pillWidth * fontScale)
}

/*
 * Type sizes are anchored to the Material 3 type scale, with one deviation:
 *
 *  † Above Title Medium the M3 scale jumps 16 → 22 → 24, too coarse for four
 *    steps; interpolated values are used where a role would force a ≥1.4x jump.
 */

private val SMALL_METRICS = AgendaMetrics(
    pillWeekday = 11.sp,
    pillDay = 16.sp,
    eventTitle = 14.sp,  // M3 Body Medium
    eventTime = 12.sp,   // M3 Body Small
    placeholder = 14.sp, // M3 Body Medium
    message = 14.sp,     // M3 Body Medium
    contentH = 36.dp,
    pillWidth = 40.dp,
    rowVPad = 4.dp,
    dayGap = 8.dp,
    monthLabel = 12.sp,
    dayHeader = 13.sp,
    dayHeaderTopPad = 10.dp,
)

private val MEDIUM_METRICS = AgendaMetrics(
    pillWeekday = 12.sp,
    pillDay = 18.sp,
    eventTitle = 16.sp,  // M3 Body Large
    eventTime = 14.sp,   // M3 Body Medium
    placeholder = 16.sp, // M3 Body Large
    message = 16.sp,     // M3 Body Large
    contentH = 40.dp,
    pillWidth = 44.dp,
    rowVPad = 5.dp,
    dayGap = 9.dp,
    monthLabel = 14.sp,
    dayHeader = 14.sp,   // M3 Title Small
    dayHeaderTopPad = 11.dp,
)

private val LARGE_METRICS = AgendaMetrics(
    pillWeekday = 12.sp,
    pillDay = 20.sp,
    eventTitle = 18.sp,  // †
    eventTime = 14.sp,   // M3 Body Medium — the secondary line steps more slowly
    placeholder = 18.sp, // †                on purpose, so the title keeps its
    message = 18.sp,     // †                lead and the hierarchy survives.
    contentH = 46.dp,
    pillWidth = 48.dp,
    rowVPad = 6.dp,
    dayGap = 10.dp,
    monthLabel = 14.sp,
    dayHeader = 16.sp,   // M3 Title Medium
    dayHeaderTopPad = 12.dp,
)

private val EXTRA_LARGE_METRICS = AgendaMetrics(
    pillWeekday = 14.sp,
    pillDay = 22.sp,
    eventTitle = 20.sp,  // †
    eventTime = 16.sp,   // M3 Body Large
    placeholder = 20.sp, // †
    message = 20.sp,     // †
    contentH = 52.dp,
    pillWidth = 52.dp,
    rowVPad = 8.dp,
    dayGap = 12.dp,
    monthLabel = 16.sp,
    dayHeader = 18.sp,   // †
    dayHeaderTopPad = 14.dp,
)

/** Indexed by [WidgetSize.ordinal] so lookup allocates nothing per recomposition. */
private val AGENDA_METRICS = listOf(
    SMALL_METRICS,
    MEDIUM_METRICS,
    LARGE_METRICS,
    EXTRA_LARGE_METRICS,
)

internal fun metricsFor(size: WidgetSize): AgendaMetrics = AGENDA_METRICS[size.ordinal]
