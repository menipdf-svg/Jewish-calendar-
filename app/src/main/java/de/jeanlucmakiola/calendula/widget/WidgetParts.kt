package de.jeanlucmakiola.calendula.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaTimeLabel
import de.jeanlucmakiola.calendula.ui.agenda.agendaTimeLabel
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.floret.glance.LocalWidgetBackground
import de.jeanlucmakiola.floret.glance.WidgetBackground
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import androidx.glance.appwidget.action.actionStartActivity

/** Cards sit one tone off the root fill, and stay opaque over a wallpaper (#292). */
@Composable
internal fun widgetCardColor(): ColorProvider = when (LocalWidgetBackground.current) {
    WidgetBackground.SOLID -> GlanceTheme.colors.surfaceVariant
    WidgetBackground.TINTED, WidgetBackground.TRANSPARENT -> GlanceTheme.colors.widgetBackground
}

/** Opens this occurrence in the app, backing out to [source]. */
internal fun EventInstance.openIntent(context: Context, source: CalendarView) = MainActivity.openEventIntent(
    context = context,
    eventId = eventId,
    beginMillis = start.toEpochMilliseconds(),
    endMillis = end.toEpochMilliseconds(),
    source = source,
)

/** Opens [date] in the app's agenda. */
internal fun openDayAction(context: Context, date: LocalDate) =
    actionStartActivity(MainActivity.openDateIntent(context, date, CalendarView.Agenda))

/** An event's time on [day]: "All day", "Starts 14:00", "Ends 9:00" or "14:00 – 15:30". */
internal fun widgetTimeLabel(context: Context, event: EventInstance, day: LocalDate, is24Hour: Boolean): String =
    when (val label = agendaTimeLabel(event, day, systemZone())) {
        AgendaTimeLabel.AllDay -> context.getString(R.string.event_detail_all_day)
        is AgendaTimeLabel.Starts -> context.getString(R.string.agenda_span_starts, formatWidgetTime(label.start, is24Hour))
        is AgendaTimeLabel.Ends -> context.getString(R.string.agenda_span_ends, formatWidgetTime(label.end, is24Hour))
        is AgendaTimeLabel.Range -> "${formatWidgetTime(label.start, is24Hour)} – ${formatWidgetTime(label.end, is24Hour)}"
    }

internal fun formatWidgetTime(instant: Instant, is24Hour: Boolean): String {
    val t = instant.toLocalDateTime(systemZone()).time
    return formatTimeOfDay(t.hour, t.minute, is24Hour, widgetLocale())
}

/** "Today" or "Tomorrow" for those two days, else null. */
internal fun relativeDayWord(context: Context, date: LocalDate, today: LocalDate): String? = when (date) {
    today -> context.getString(R.string.agenda_header_today)
    today.plus(1, DateTimeUnit.DAY) -> context.getString(R.string.agenda_header_tomorrow)
    else -> null
}

/** "Today", "Tomorrow", or the short weekday within the coming week, else the short date. */
internal fun relativeDayLabel(context: Context, date: LocalDate, today: LocalDate): String {
    val locale = widgetLocale()
    return relativeDayWord(context, date, today) ?: when {
        date < today.plus(7, DateTimeUnit.DAY) && date > today ->
            date.dayOfWeek.toJavaDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, locale)
        else -> localizedDateFormatter(locale, "dMMM").format(date.toJavaLocalDate())
    }
}
