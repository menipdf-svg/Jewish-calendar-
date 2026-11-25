package de.jeanlucmakiola.calendula.widget.split

import de.jeanlucmakiola.calendula.domain.isCrossedOut
import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.groupAgendaDays
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.widget.AgendaWidgetOptions
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.MonthWidgetSource
import de.jeanlucmakiola.calendula.widget.Themed
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.WidgetEventRow
import de.jeanlucmakiola.calendula.widget.WidgetSettings
import de.jeanlucmakiola.calendula.widget.widgetLoadContext
import de.jeanlucmakiola.calendula.widget.compactRowMetrics
import de.jeanlucmakiola.calendula.widget.firstDayOfMonthIndex
import de.jeanlucmakiola.calendula.widget.loadCachedMonthSource
import de.jeanlucmakiola.calendula.widget.monthIndexOf
import de.jeanlucmakiola.calendula.widget.monthWindowCenter
import de.jeanlucmakiola.calendula.widget.relativeDayLabel
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import de.jeanlucmakiola.calendula.widget.weekOf
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.widgetCardColor
import de.jeanlucmakiola.calendula.widget.widgetEntryPoint
import de.jeanlucmakiola.calendula.widget.widgetSettings
import de.jeanlucmakiola.calendula.widget.widgetState
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.toJavaLocalDate
import de.jeanlucmakiola.calendula.widget.widgetTimeLabel
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.glance.WidgetActionStyle
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetHeader
import de.jeanlucmakiola.floret.glance.WidgetHeaderAction
import de.jeanlucmakiola.floret.glance.WidgetHeaderActionGap
import de.jeanlucmakiola.floret.glance.WidgetHeaderTextAction
import de.jeanlucmakiola.floret.glance.WidgetMessage
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.format.TextStyle as JavaTextStyle

/** Per-widget state: the shown month (`year * 12 + monthOrdinal`) and the selected day; both follow today when absent. */
private val MONTH_INDEX_KEY = intPreferencesKey("split_month_index")
private val SELECTED_KEY = stringPreferencesKey("split_selected")

/** What the month + day widget draws: the month widget's window, plus what its list needs. */
sealed interface MonthDayData {
    data object NeedsPermission : MonthDayData
    data class Ready(
        val source: MonthWidgetSource.Ready,
        val options: AgendaWidgetOptions,
        val is24Hour: Boolean,
        val now: Instant,
    ) : MonthDayData
}

/** [loaded]'s month window with what the day list draws by. */
internal suspend fun Context.loadMonthDay(
    loaded: Themed<MonthWidgetSource>,
    options: AgendaWidgetOptions,
    settings: WidgetSettings? = null,
): Themed<MonthDayData> {
    val ready = loaded.content as? MonthWidgetSource.Ready ?: return Themed(MonthDayData.NeedsPermission, loaded.appTheme)
    val settings = settings ?: widgetEntryPoint().settingsPrefs().widgetSettings.first()
    return Themed(
        MonthDayData.Ready(
            source = ready,
            options = options,
            is24Hour = settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(this)),
            now = Clock.System.now(),
        ),
        loaded.appTheme,
    )
}

/**
 * Month + day (#441): a compact month grid beside the selected day's events,
 * like Samsung Calendar's. A day in the grid selects it, starting on today;
 * the list and its events open the app. The plain month widget keeps
 * tap-opens-the-day (#386).
 */
class MonthDayWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.MONTH_DAY)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.MONTH_DAY)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stamp = refreshStamp(context, id)
        val shown = context.widgetState(id)[MONTH_INDEX_KEY] ?: monthIndexOf(today(systemZone()))
        val center = monthWindowCenter(shown)
        val load: suspend (Int, Long) -> Themed<MonthDayData> = { c, s ->
            val ctx = context.widgetLoadContext(id)
            context.loadMonthDay(context.loadCachedMonthSource(id, c, s, ctx), ctx.options, ctx.settings)
        }
        val widgetData = WidgetData(load(center, stamp), center, stamp)
        provideContent {
            val index = currentState(MONTH_INDEX_KEY) ?: monthIndexOf(today(systemZone()))
            val currentCenter = monthWindowCenter(index)
            val currentStamp = currentRefreshStamp()
            val loaded = widgetData.current(currentCenter, currentStamp) { load(currentCenter, currentStamp) }
            val selected = currentState(SELECTED_KEY)?.let(LocalDate::parse)
            InAppLanguage {
                MonthDayWidgetContent(loaded.content, index, selected, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class MonthDayWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthDayWidget()
}

/** Selects the tapped day; the list redraws from what's loaded. */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val date = parameters[dateKey] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            if (date == today(systemZone()).toString()) prefs.remove(SELECTED_KEY) else prefs[SELECTED_KEY] = date
        }
        MonthDayWidget().update(context.applicationContext, glanceId)
    }

    companion object {
        val dateKey = ActionParameters.Key<String>("date")
    }
}

/** Pages the grid by the `delta` parameter; landing on the current month follows the date again. */
class ShiftMonthDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: 0
        val current = monthIndexOf(today(systemZone()))
        // The selection moves with the page — to today on this month, else the
        // 1st — so the list never shows a day outside the loaded window.
        updateAppWidgetState(context, glanceId) { prefs ->
            val next = (prefs[MONTH_INDEX_KEY] ?: current) + delta
            if (next == current) {
                prefs.remove(MONTH_INDEX_KEY)
                prefs.remove(SELECTED_KEY)
            } else {
                prefs[MONTH_INDEX_KEY] = next
                prefs[SELECTED_KEY] = firstDayOfMonthIndex(next).toString()
            }
        }
        MonthDayWidget().update(context.applicationContext, glanceId)
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("delta")
    }
}

/** Back to this month with today selected. */
class ResetMonthDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(MONTH_INDEX_KEY)
            prefs.remove(SELECTED_KEY)
        }
        MonthDayWidget().update(context.applicationContext, glanceId)
    }
}

/** The grid's days: whole weeks from [weekStart] covering [index]'s month. */
internal fun monthGridDays(index: Int, weekStart: DayOfWeek): List<LocalDate> {
    val first = firstDayOfMonthIndex(index)
    val start = weekOf(first, weekStart).start
    val last = first.plus(1, DateTimeUnit.MONTH).plus(-1, DateTimeUnit.DAY)
    val weeks = (start.daysUntilInclusive(last) + 6) / 7
    return List(weeks * 7) { start.plus(it, DateTimeUnit.DAY) }
}

private fun LocalDate.daysUntilInclusive(other: LocalDate): Int =
    (other.toEpochDays() - toEpochDays()).toInt() + 1

private val GridGap = 8.dp

@Composable
internal fun MonthDayWidgetContent(
    data: MonthDayData,
    index: Int,
    selected: LocalDate?,
    appearance: WidgetAppearance,
    appTheme: ThemeMode,
    preview: Boolean = false,
) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        when (data) {
            MonthDayData.NeedsPermission -> WidgetMessage(context.getString(R.string.widget_needs_permission))
            is MonthDayData.Ready -> {
                val source = data.source
                val day = selected ?: source.today
                val zone = systemZone()
                val days = monthGridDays(index, source.weekStart)
                // Declined and cancelled events are no plan for the day, as on the week strip (#388).
                val planned = source.instances.filterNot { it.isCrossedOut }
                val busy = groupAgendaDays(days.first(), days.last(), planned, zone).map { it.date }.toSet()
                Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Header(index, source.today)
                    Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        val gridWidth = (LocalSize.current.width - 16.dp - GridGap) * 0.52f
                        Box(modifier = GlanceModifier.width(gridWidth).fillMaxHeight()) {
                            Grid(days, index, source, day, busy, gridWidth)
                        }
                        Spacer(GlanceModifier.width(GridGap))
                        Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            DayList(day, data, preview)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(index: Int, today: LocalDate) {
    val context = LocalContext.current
    WidgetHeader(
        title = formatCalendarTitle(
            date = firstDayOfMonthIndex(index).toJavaLocalDate(),
            locale = widgetLocale(),
            currentYear = today.year,
            skeleton = "LLLL",
        ),
        titleAction = actionStartActivity(MainActivity.openViewIntent(context, CalendarView.Month)),
        modifier = GlanceModifier.padding(bottom = 2.dp),
        titleInset = 4.dp,
    ) {
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_chevron_left),
            contentDescription = context.getString(R.string.widget_prev_month),
            action = actionRunCallback<ShiftMonthDayAction>(actionParametersOf(ShiftMonthDayAction.deltaKey to -1)),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        WidgetHeaderTextAction(
            text = today.day.toString(),
            contentDescription = context.getString(R.string.widget_today),
            action = actionRunCallback<ResetMonthDayAction>(),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_chevron_right),
            contentDescription = context.getString(R.string.widget_next_month),
            action = actionRunCallback<ShiftMonthDayAction>(actionParametersOf(ShiftMonthDayAction.deltaKey to 1)),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = context.getString(R.string.widget_new_event),
            action = actionStartActivity(MainActivity.openCreateIntent(context, today)),
        )
    }
}

/**
 * The month as numbers: selected day filled, today in bold primary, days
 * with events dotted, days around the month faded. Three views a day keeps
 * a six-week month well inside Glance's view ids (#372).
 */
@Composable
private fun Grid(
    days: List<LocalDate>,
    index: Int,
    source: MonthWidgetSource.Ready,
    selected: LocalDate,
    busy: Set<LocalDate>,
    width: Dp,
) {
    val locale = widgetLocale()
    val month = firstDayOfMonthIndex(index).month.ordinal + 1
    val backing = GlanceModifier.wallpaperBacking(radius = 14.dp)
    Column(modifier = backing.fillMaxSize().padding(2.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    text = java.time.DayOfWeek.of((source.weekStart.ordinal + i) % 7 + 1).getDisplayName(JavaTextStyle.NARROW, locale),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.width((width - 4.dp) / 7),
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                week.forEach { date ->
                    DayCell(date, date.month.ordinal + 1 == month, date == source.today, date == selected, date in busy, (width - 4.dp) / 7)
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, hasEvents: Boolean, width: Dp) {
    val ink = when {
        isSelected -> GlanceTheme.colors.onPrimary
        isToday -> GlanceTheme.colors.primary
        inMonth -> GlanceTheme.colors.onSurface
        else -> GlanceTheme.colors.outline
    }
    // Always set: the host reapplies onto reused views, so a dropped background would linger.
    val fill = if (isSelected) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)
    Column(
        modifier = GlanceModifier.cornerRadius(10.dp).background(fill)
            .width(width)
            .fillMaxHeight()
            .clickable(actionRunCallback<SelectDayAction>(actionParametersOf(SelectDayAction.dateKey to date.toString()))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.day.toString(),
            maxLines = 1,
            style = TextStyle(
                color = ink,
                fontSize = 11.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = if (hasEvents) "•" else " ",
            maxLines = 1,
            style = TextStyle(color = if (isSelected) ink else GlanceTheme.colors.primary, fontSize = 7.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun DayList(day: LocalDate, data: MonthDayData.Ready, preview: Boolean) {
    val context = LocalContext.current
    val source = data.source
    val zone = systemZone()
    val events = groupAgendaDays(day, day, source.instances, zone).firstOrNull()?.events.orEmpty()
        .let { all -> if (data.options.pastDisplay == PastEventDisplay.HIDE) all.filterNot { it.hasEnded(data.now) } else all }
    val metrics = compactRowMetrics(data.options.textSize, context.resources.configuration.fontScale)
    val openDay = actionStartActivity(MainActivity.openDateIntent(context, day, CalendarView.Month))
    Column(modifier = GlanceModifier.fillMaxSize()) {
        val date = localizedDateFormatter(widgetLocale(), "EEEdMMM").format(day.toJavaLocalDate())
        val label = when (day) {
            source.today, source.today.plus(1, DateTimeUnit.DAY) -> "${relativeDayLabel(context, day, source.today)} · $date"
            else -> date
        }
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = metrics.time, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier
                .padding(bottom = 4.dp)
                .wallpaperBacking(radius = 8.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clickable(openDay),
        )
        if (events.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().cornerRadius(14.dp).background(widgetCardColor()).clickable(openDay),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = context.getString(R.string.widget_day_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = metrics.time, textAlign = TextAlign.Center),
                    modifier = GlanceModifier.padding(8.dp),
                )
            }
        } else {
            val row: @Composable (Int) -> Unit = { i ->
                val event = events[i]
                WidgetEventRow(
                    event = event,
                    secondary = widgetTimeLabel(context, event, day, data.is24Hour),
                    soften = source.soften,
                    metrics = metrics,
                    position = positionOf(i, events.size),
                    source = CalendarView.Month,
                    dimmed = data.options.pastDisplay == PastEventDisplay.DIM && event.hasEnded(data.now),
                )
            }
            if (preview) {
                Column(modifier = GlanceModifier.fillMaxSize()) { repeat(minOf(events.size, 8)) { row(it) } }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) { items(events.size) { row(it) } }
            }
        }
    }
}
