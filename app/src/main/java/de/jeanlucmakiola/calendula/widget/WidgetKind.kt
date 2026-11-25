package de.jeanlucmakiola.calendula.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.month.MonthWidget
import de.jeanlucmakiola.calendula.widget.month.MonthWidgetReceiver
import de.jeanlucmakiola.calendula.widget.quickadd.QuickAddWidget
import de.jeanlucmakiola.calendula.widget.quickadd.QuickAddWidgetReceiver
import de.jeanlucmakiola.calendula.widget.date.DateWidget
import de.jeanlucmakiola.calendula.widget.date.DateWidgetReceiver
import de.jeanlucmakiola.calendula.widget.week.WeekStripWidget
import de.jeanlucmakiola.calendula.widget.week.WeekStripWidgetReceiver
import de.jeanlucmakiola.calendula.widget.upcoming.UpcomingWidget
import de.jeanlucmakiola.calendula.widget.upcoming.UpcomingWidgetReceiver
import de.jeanlucmakiola.calendula.widget.today.TodayWidget
import de.jeanlucmakiola.calendula.widget.today.TodayWidgetReceiver
import de.jeanlucmakiola.calendula.widget.countdown.CountdownWidget
import de.jeanlucmakiola.calendula.widget.countdown.CountdownWidgetReceiver
import de.jeanlucmakiola.calendula.widget.split.MonthDayWidget
import de.jeanlucmakiola.calendula.widget.split.MonthDayWidgetReceiver

/** A content option a widget's config screen offers, besides background and theme. */
enum class WidgetOption { STYLE, RANGE, TEXT_SIZE, PAST_DISPLAY, SHOW_TODAY, CALENDARS }

/**
 * Every widget the app ships, told apart by their receiver: what redraws,
 * the rollover and the config screen go through.
 */
enum class WidgetKind(
    val receiver: Class<*>,
    val label: Int,
    val options: Set<WidgetOption>,
    /** The size a config preview draws at before the launcher reports one. */
    val defaultSize: DpSize,
) {
    AGENDA(
        AgendaWidgetReceiver::class.java,
        R.string.widget_agenda_label,
        setOf(
            WidgetOption.STYLE,
            WidgetOption.RANGE,
            WidgetOption.TEXT_SIZE,
            WidgetOption.PAST_DISPLAY,
            WidgetOption.SHOW_TODAY,
            WidgetOption.CALENDARS,
        ),
        DpSize(260.dp, 280.dp),
    ),
    MONTH(
        MonthWidgetReceiver::class.java,
        R.string.widget_month_label,
        setOf(WidgetOption.CALENDARS),
        DpSize(320.dp, 340.dp),
    ),
    QUICK_ADD(
        QuickAddWidgetReceiver::class.java,
        R.string.widget_quick_add_label,
        emptySet(),
        DpSize(76.dp, 76.dp),
    ),
    DATE(
        DateWidgetReceiver::class.java,
        R.string.widget_date_label,
        emptySet(),
        DpSize(76.dp, 76.dp),
    ),
    WEEK_STRIP(
        WeekStripWidgetReceiver::class.java,
        R.string.widget_week_strip_label,
        setOf(WidgetOption.CALENDARS),
        DpSize(300.dp, 90.dp),
    ),
    UPCOMING(
        UpcomingWidgetReceiver::class.java,
        R.string.widget_upcoming_label,
        setOf(WidgetOption.RANGE, WidgetOption.TEXT_SIZE, WidgetOption.CALENDARS),
        DpSize(180.dp, 180.dp),
    ),
    TODAY(
        TodayWidgetReceiver::class.java,
        R.string.widget_today_label,
        setOf(WidgetOption.TEXT_SIZE, WidgetOption.PAST_DISPLAY, WidgetOption.CALENDARS),
        DpSize(300.dp, 100.dp),
    ),
    COUNTDOWN(
        CountdownWidgetReceiver::class.java,
        R.string.widget_countdown_label,
        setOf(WidgetOption.CALENDARS),
        DpSize(200.dp, 100.dp),
    ),
    MONTH_DAY(
        MonthDayWidgetReceiver::class.java,
        R.string.widget_month_day_label,
        setOf(WidgetOption.TEXT_SIZE, WidgetOption.PAST_DISPLAY, WidgetOption.CALENDARS),
        DpSize(320.dp, 200.dp),
    ),
    ;

    /** Whether a calendar change can change what it draws; Date and Quick add draw no events. */
    val drawsEvents: Boolean get() = this != QUICK_ADD && this != DATE

    fun widget(): GlanceAppWidget = when (this) {
        AGENDA -> AgendaWidget()
        MONTH -> MonthWidget()
        QUICK_ADD -> QuickAddWidget()
        DATE -> DateWidget()
        WEEK_STRIP -> WeekStripWidget()
        UPCOMING -> UpcomingWidget()
        TODAY -> TodayWidget()
        COUNTDOWN -> CountdownWidget()
        MONTH_DAY -> MonthDayWidget()
    }

    /** Whether this kind stores [AgendaWidgetOptions] of its own. */
    val hasContentOptions: Boolean
        get() = options.any { it != WidgetOption.CALENDARS }

    companion object {
        fun of(context: Context, appWidgetId: Int): WidgetKind? {
            val provider = AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)?.provider ?: return null
            return entries.firstOrNull { it.receiver.name == provider.className }
        }
    }
}
