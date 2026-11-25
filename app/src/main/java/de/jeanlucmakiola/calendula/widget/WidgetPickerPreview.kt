package de.jeanlucmakiola.calendula.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.groupAgendaDays
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidgetContent
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.countdown.CountdownData
import de.jeanlucmakiola.calendula.widget.countdown.CountdownOptions
import de.jeanlucmakiola.calendula.widget.countdown.CountdownWidgetContent
import de.jeanlucmakiola.calendula.widget.countdown.CountdownWidgetReceiver
import de.jeanlucmakiola.calendula.widget.date.DateWidgetContent
import de.jeanlucmakiola.calendula.widget.date.DateWidgetReceiver
import de.jeanlucmakiola.calendula.widget.month.MonthWidgetContent
import de.jeanlucmakiola.calendula.widget.month.MonthWidgetReceiver
import de.jeanlucmakiola.calendula.widget.quickadd.QuickAddWidgetContent
import de.jeanlucmakiola.calendula.widget.quickadd.QuickAddWidgetReceiver
import de.jeanlucmakiola.calendula.widget.split.MonthDayData
import de.jeanlucmakiola.calendula.widget.split.MonthDayWidgetContent
import de.jeanlucmakiola.calendula.widget.split.MonthDayWidgetReceiver
import de.jeanlucmakiola.calendula.widget.today.TodayWidgetContent
import de.jeanlucmakiola.calendula.widget.today.TodayWidgetReceiver
import de.jeanlucmakiola.calendula.widget.upcoming.UpcomingWidgetContent
import de.jeanlucmakiola.calendula.widget.upcoming.UpcomingWidgetReceiver
import de.jeanlucmakiola.calendula.widget.week.WeekStripWidgetContent
import de.jeanlucmakiola.calendula.widget.week.WeekStripWidgetReceiver
import de.jeanlucmakiola.floret.glance.publishWidgetPreviews
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Republishes the generated picker previews (Android 15+) when anything they
 * draw with has changed: the app version, language, today's date, or the
 * theme, week start, time format and colour softening.
 */
internal suspend fun publishCalendulaWidgetPreviews(context: Context) {
    val settings = context.widgetEntryPoint().settingsPrefs().widgetSettings.first()
    val updated = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    val is24Hour = settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(context))
    publishWidgetPreviews(
        context,
        version = listOf(
            updated,
            widgetLocale().toLanguageTag(),
            today(systemZone()),
            settings.themeMode,
            settings.weekStart,
            is24Hour,
            settings.soften,
        ).joinToString("-"),
        receivers = listOf(
            AgendaWidgetReceiver::class,
            MonthWidgetReceiver::class,
            QuickAddWidgetReceiver::class,
            DateWidgetReceiver::class,
            WeekStripWidgetReceiver::class,
            UpcomingWidgetReceiver::class,
            TodayWidgetReceiver::class,
            CountdownWidgetReceiver::class,
            MonthDayWidgetReceiver::class,
        ),
    )
}

/** The size [kind]'s picker preview is laid out at. */
internal fun previewSizeOf(kind: WidgetKind) = SizeMode.Responsive(setOf(kind.defaultSize))

/** [kind]'s picker preview: the real widget, drawn with sample events around today's morning. */
internal suspend fun GlanceAppWidget.provideSamplePreview(context: Context, kind: WidgetKind) {
    val settings = context.widgetEntryPoint().settingsPrefs().widgetSettings.first()
    val zone = systemZone()
    val today = today(zone)
    val now = today.atTime(8, 0).toInstant(zone)
    val instances = context.sampleInstances(today, zone)
    val weekStart = settings.weekStart.resolveFirstDay(widgetLocale())
    val is24Hour = settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(context))
    val options = settings.agendaDefaults
    val appearance = WidgetAppearance()
    val index = monthIndexOf(today)
    val month = MonthWidgetSource.Ready(today, weekStart, settings.soften, instances, index..index)
    fun days(range: ClosedRange<LocalDate>) = DaysWidgetData.Ready(
        today = today,
        firstDay = range.start,
        lastDay = range.endInclusive,
        days = groupAgendaDays(range.start, range.endInclusive, instances, zone),
        weekStart = weekStart,
        is24Hour = is24Hour,
        soften = settings.soften,
        options = options,
        now = now,
    )
    val theme = settings.themeMode
    provideContent {
        when (kind) {
            WidgetKind.AGENDA -> AgendaWidgetContent(
                AgendaWidgetData.Ready(
                    today = today,
                    days = groupAgendaDays(today, today.plus(6, DateTimeUnit.DAY), instances, zone),
                    is24Hour = is24Hour,
                    soften = settings.soften,
                    pastDisplay = options.pastDisplay,
                    showToday = options.showToday,
                    textSize = options.textSize,
                    now = now,
                ),
                appearance,
                theme,
                preview = true,
            )
            WidgetKind.MONTH -> MonthWidgetContent(month, index, appearance, theme)
            WidgetKind.MONTH_DAY -> MonthDayWidgetContent(
                MonthDayData.Ready(month, options, is24Hour, now),
                index,
                selected = null,
                appearance = appearance,
                appTheme = theme,
                preview = true,
            )
            WidgetKind.QUICK_ADD -> QuickAddWidgetContent(today, appearance, theme)
            WidgetKind.DATE -> DateWidgetContent(today, appearance, theme)
            WidgetKind.WEEK_STRIP -> WeekStripWidgetContent(days(weekOf(today, weekStart)), appearance, theme)
            WidgetKind.UPCOMING -> UpcomingWidgetContent(days(today..today.plus(6, DateTimeUnit.DAY)), appearance, theme)
            WidgetKind.TODAY -> TodayWidgetContent(days(today..today.plus(1, DateTimeUnit.DAY)), appearance, theme, preview = true)
            WidgetKind.COUNTDOWN -> CountdownWidgetContent(
                CountdownData.Ready(
                    options = CountdownOptions(),
                    event = instances.first { !it.isAllDay && it.start > now },
                    now = now,
                    today = today,
                    is24Hour = is24Hour,
                    soften = settings.soften,
                    appWidgetId = null,
                ),
                appearance,
                theme,
            )
        }
    }
}

/** A plausible few weeks: a busy today, a trip tomorrow, and a scatter of days with something on. */
private fun Context.sampleInstances(today: LocalDate, zone: TimeZone): List<EventInstance> {
    var id = 0L
    fun timed(day: Int, at: LocalTime, length: kotlin.time.Duration, title: Int, color: Int): EventInstance {
        val start = today.plus(day, DateTimeUnit.DAY).atTime(at).toInstant(zone)
        id++
        return EventInstance(id, id, 1, getString(title), start, start + length, false, color, null)
    }
    fun allDay(day: Int, title: Int, color: Int): EventInstance {
        val date = today.plus(day, DateTimeUnit.DAY)
        id++
        return EventInstance(
            id, id, 1, getString(title),
            date.atStartOfDayIn(TimeZone.UTC), date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.UTC),
            true, color, null,
        )
    }
    return listOf(
        timed(0, LocalTime(9, 30), 30.minutes, R.string.settings_past_events_sample_morning, Blue),
        timed(0, LocalTime(12, 30), 1.hours, R.string.settings_past_events_sample_midday, Green),
        timed(0, LocalTime(19, 0), 2.hours, R.string.settings_past_events_sample_evening, Purple),
        allDay(1, R.string.widget_preview_sample_trip, Orange),
        timed(2, LocalTime(10, 0), 1.hours, R.string.widget_preview_sample_dentist, Red),
        timed(-6, LocalTime(9, 30), 30.minutes, R.string.settings_past_events_sample_morning, Blue),
        timed(-2, LocalTime(19, 0), 2.hours, R.string.settings_past_events_sample_evening, Purple),
        timed(5, LocalTime(19, 0), 2.hours, R.string.settings_past_events_sample_evening, Purple),
        timed(8, LocalTime(9, 30), 30.minutes, R.string.settings_past_events_sample_morning, Blue),
        timed(12, LocalTime(12, 30), 1.hours, R.string.settings_past_events_sample_midday, Green),
    ).sortedBy { it.start }
}

private const val Blue = 0xFF039BE5.toInt()
private const val Green = 0xFF33B679.toInt()
private const val Purple = 0xFF8E24AA.toInt()
private const val Orange = 0xFFF4511E.toInt()
private const val Red = 0xFFD50000.toInt()
