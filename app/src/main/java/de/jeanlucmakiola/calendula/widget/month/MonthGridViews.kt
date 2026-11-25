package de.jeanlucmakiola.calendula.widget.month

import android.app.PendingIntent
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.isCrossedOut
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.floret.glance.RemoteColor
import de.jeanlucmakiola.floret.glance.WidgetThemeMode
import de.jeanlucmakiola.floret.glance.remoteColor
import de.jeanlucmakiola.floret.glance.setImageTint
import de.jeanlucmakiola.floret.glance.setTextColor
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

/** The theme colours the plain-RemoteViews grid draws with. */
internal data class GridColors(
    val onSurface: RemoteColor,
    val onSurfaceVariant: RemoteColor,
    val primary: RemoteColor,
    val onPrimary: RemoteColor,
    val cell: RemoteColor,
)

/**
 * The month's week rows as one plain RemoteViews tree, embedded in the Glance
 * widget so its views don't draw on Glance's view-id pool (#372): every event
 * gets its bar, and only what can't fit the widget's height folds into "+N".
 *
 * A host may reapply a new rendering onto the views of the last one rather than
 * inflate it afresh, so every container is cleared before it's filled and every
 * view state is set both ways, never just when it differs from the layout.
 */
internal fun monthGridViews(
    context: Context,
    layouts: List<WeekLayout>,
    currentMonth: Month,
    today: LocalDate,
    soften: Boolean,
    colors: GridColors,
    mode: WidgetThemeMode,
): RemoteViews {
    val grid = RemoteViews(context.packageName, R.layout.widget_month_grid)
    grid.removeAllViews(R.id.month_grid)
    layouts.forEach { grid.addView(R.id.month_grid, weekViews(context, it, currentMonth, today, soften, colors, mode)) }
    return grid
}

private fun weekViews(
    context: Context,
    layout: WeekLayout,
    currentMonth: Month,
    today: LocalDate,
    soften: Boolean,
    colors: GridColors,
    mode: WidgetThemeMode,
): RemoteViews {
    val week = RemoteViews(context.packageName, R.layout.widget_month_week)
    week.removeAllViews(R.id.week_lanes)
    layout.week.days.forEachIndexed { col, date ->
        week.setOnClickPendingIntent(STRIP_IDS[col], openDayIntent(context, date))
        // Each day its own rounded cell, as in the app's month grid. Days around
        // the month get a shade over their (opaque) cell, so they recede without
        // letting a wallpaper show through.
        week.setImageTint(context, CELL_IDS[col], colors.cell)
        val outside = date.month != currentMonth
        week.setViewVisibility(CELL_DIM_IDS[col], if (outside) View.VISIBLE else View.GONE)
        if (outside) {
            week.setImageTint(context, CELL_DIM_IDS[col], mode.remoteColor(OUTSIDE_SHADE_DAY, OUTSIDE_SHADE_NIGHT))
        }
        val dayId = DAY_IDS[col]
        val isToday = date == today
        week.setTextViewText(dayId, if (isToday) bold(date.day.toString()) else date.day.toString())
        week.setTextColor(context, dayId, when {
            isToday -> colors.onPrimary
            date.month == currentMonth -> colors.onSurface
            else -> colors.onSurfaceVariant
        })
        week.setViewVisibility(TODAY_IDS[col], if (isToday) View.VISIBLE else View.GONE)
        if (isToday) week.setImageTint(context, TODAY_IDS[col], colors.primary)
    }
    layout.mixedLane?.let { cells ->
        val lane = laneViews(context, layout.compact)
        cells.forEach { cell ->
            lane.addView(
                R.id.lane,
                when (val item = cell.item) {
                    null -> gap(context, cell.cols)
                    is LaneItem.Bar -> bar(context, item.event, cell.cols, soften, mode)
                    is LaneItem.Count -> more(context, item.count, colors)
                },
            )
        }
        week.addView(R.id.week_lanes, lane)
        return week
    }
    layout.lanes.forEach { cells ->
        val lane = laneViews(context)
        cells.forEach { cell ->
            val event = cell.item
            lane.addView(R.id.lane, if (event == null) gap(context, cell.cols) else bar(context, event, cell.cols, soften, mode))
        }
        week.addView(R.id.week_lanes, lane)
    }
    if (layout.showsOverflow && layout.overflow.isNotEmpty()) {
        val lane = laneViews(context)
        layout.overflow.forEach { cell ->
            val count = cell.item
            if (count == null) {
                lane.addView(R.id.lane, gap(context, cell.cols))
            } else {
                lane.addView(R.id.lane, more(context, count, colors))
            }
        }
        week.addView(R.id.week_lanes, lane)
    }
    return week
}

private fun more(context: Context, count: Int, colors: GridColors) =
    RemoteViews(context.packageName, R.layout.widget_month_more).apply {
        setTextViewText(R.id.more, "+$count")
        setTextColor(context, R.id.more, colors.onSurfaceVariant)
    }

private fun laneViews(context: Context, compact: Boolean = false) =
    RemoteViews(context.packageName, if (compact) R.layout.widget_month_lane_compact else R.layout.widget_month_lane)
        .apply { removeAllViews(R.id.lane) }

private fun gap(context: Context, cols: Int) = RemoteViews(context.packageName, GAP_LAYOUTS[cols - 1])

private fun bar(context: Context, event: EventInstance, cols: Int, soften: Boolean, mode: WidgetThemeMode): RemoteViews {
    val views = RemoteViews(context.packageName, BAR_LAYOUTS[cols - 1])
    val dayFill = eventFill(event.color, dark = false, soften)
    val nightFill = eventFill(event.color, dark = true, soften)
    views.setImageTint(context, R.id.bar_bg, mode.remoteColor(dayFill, nightFill))
    val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
    // A span, like Glance's own decoration: paint flags would drop the text's defaults.
    views.setTextViewText(R.id.bar_title, if (event.isCrossedOut) span(title, StrikethroughSpan()) else title)
    views.setTextColor(context, R.id.bar_title, mode.remoteColor(eventInk(dayFill), eventInk(nightFill)))
    return views
}

private fun bold(text: String): CharSequence = span(text, StyleSpan(Typeface.BOLD))

private fun span(text: String, span: Any): CharSequence =
    SpannableString(text).apply { setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

private fun openDayIntent(context: Context, date: LocalDate): PendingIntent = PendingIntent.getActivity(
    context,
    date.toEpochDays().toInt(),
    MainActivity.openDateIntent(context, date, CalendarView.Month),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private val STRIP_IDS = intArrayOf(R.id.strip_0, R.id.strip_1, R.id.strip_2, R.id.strip_3, R.id.strip_4, R.id.strip_5, R.id.strip_6)
/** The shade over a day outside the month: a dark cell needs more of it to read as dimmed. Mirrored by `widget_preview_outside_shade`. */
private val OUTSIDE_SHADE_DAY = Color.Black.copy(alpha = 0.11f)
private val OUTSIDE_SHADE_NIGHT = Color.Black.copy(alpha = 0.30f)

private val CELL_DIM_IDS = intArrayOf(
    R.id.cell_dim_0, R.id.cell_dim_1, R.id.cell_dim_2, R.id.cell_dim_3, R.id.cell_dim_4, R.id.cell_dim_5, R.id.cell_dim_6,
)
private val CELL_IDS = intArrayOf(R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3, R.id.cell_4, R.id.cell_5, R.id.cell_6)
private val DAY_IDS = intArrayOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)
private val TODAY_IDS = intArrayOf(
    R.id.today_bg_0, R.id.today_bg_1, R.id.today_bg_2, R.id.today_bg_3, R.id.today_bg_4, R.id.today_bg_5, R.id.today_bg_6,
)
private val BAR_LAYOUTS = intArrayOf(
    R.layout.widget_month_bar_1, R.layout.widget_month_bar_2, R.layout.widget_month_bar_3, R.layout.widget_month_bar_4,
    R.layout.widget_month_bar_5, R.layout.widget_month_bar_6, R.layout.widget_month_bar_7,
)
private val GAP_LAYOUTS = intArrayOf(
    R.layout.widget_month_gap_1, R.layout.widget_month_gap_2, R.layout.widget_month_gap_3, R.layout.widget_month_gap_4,
    R.layout.widget_month_gap_5, R.layout.widget_month_gap_6, R.layout.widget_month_gap_7,
)
