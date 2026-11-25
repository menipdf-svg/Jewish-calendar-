package de.jeanlucmakiola.calendula.widget.today

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.DaysWidgetData
import de.jeanlucmakiola.calendula.widget.Themed
import de.jeanlucmakiola.calendula.widget.WidgetTicks
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.domain.localEnd
import androidx.glance.appwidget.GlanceAppWidgetManager
import de.jeanlucmakiola.calendula.widget.EventRowMetrics
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.WidgetEventRow
import de.jeanlucmakiola.calendula.widget.WidgetLoadContext
import de.jeanlucmakiola.calendula.widget.compactRowMetrics
import de.jeanlucmakiola.calendula.widget.loadDays
import de.jeanlucmakiola.calendula.widget.relativeDayLabel
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.widgetLoadContext
import de.jeanlucmakiola.calendula.widget.widgetTimeLabel
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetMessage
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import java.time.format.TextStyle as JavaTextStyle

/**
 * Today list (#438): only today's events beside today's date pill. Once
 * they're over, or with none at all, it says so and shows tomorrow's first
 * one or two events in a quieter style.
 */
class TodayWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Exact: whether one or two of tomorrow's events fit follows the height.
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.TODAY)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.TODAY)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadPlacedToday(id), refreshStamp(context, id))
        provideContent {
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadPlacedToday(id) }
            InAppLanguage {
                TodayWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class TodayWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

internal suspend fun Context.loadTodayList(load: WidgetLoadContext) = loadDays(load) { today, _ ->
    today..today.plus(1, DateTimeUnit.DAY)
}

/** [loadTodayList] for placed widget [id], arming its redraw for when the next of today's events ends. */
private suspend fun Context.loadPlacedToday(id: GlanceId): Themed<DaysWidgetData> {
    val loaded = loadTodayList(widgetLoadContext(id))
    val appWidgetId = GlanceAppWidgetManager(this).getAppWidgetId(id)
    val data = loaded.content as? DaysWidgetData.Ready
    val zone = systemZone()
    val nextEnd = data?.eventsOn(data.today)?.map { it.localEnd(zone) }?.filter { it > data.now }?.minOrNull()
    if (nextEnd == null) {
        WidgetTicks.cancel(this, WidgetKind.TODAY, appWidgetId)
    } else {
        WidgetTicks.schedule(this, WidgetKind.TODAY, appWidgetId, nextEnd)
    }
    return loaded
}

private val PillWidth = 44.dp
private val RowGap = 2.dp

/** What the list side shows: today's events, or the look ahead once none are left. */
internal sealed interface TodayRows {
    data class Today(val events: List<EventInstance>) : TodayRows
    data class NothingLeft(val tomorrow: List<EventInstance>) : TodayRows
}

internal fun todayRows(data: DaysWidgetData.Ready): TodayRows {
    val today = data.eventsOn(data.today)
    if (today.none { !it.hasEnded(data.now) }) {
        return TodayRows.NothingLeft(data.eventsOn(data.today.plus(1, DateTimeUnit.DAY)))
    }
    val shown = if (data.options.pastDisplay == PastEventDisplay.HIDE) today.filterNot { it.hasEnded(data.now) } else today
    return TodayRows.Today(shown)
}

/** The whole widget, shared with its config preview, which draws its list as a plain column. */
@Composable
internal fun TodayWidgetContent(
    data: DaysWidgetData,
    appearance: WidgetAppearance,
    appTheme: ThemeMode,
    preview: Boolean = false,
) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        when (data) {
            DaysWidgetData.NeedsPermission -> WidgetMessage(context.getString(R.string.widget_needs_permission))
            is DaysWidgetData.Ready -> Row(modifier = GlanceModifier.fillMaxSize().padding(6.dp)) {
                TodayPill(data.today)
                Spacer(GlanceModifier.width(6.dp))
                val metrics = compactRowMetrics(data.options.textSize, context.resources.configuration.fontScale)
                Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    when (val rows = todayRows(data)) {
                        is TodayRows.Today -> TodayList(rows.events, data, metrics, preview)
                        is TodayRows.NothingLeft -> NothingLeft(rows.tomorrow, data, metrics)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayList(events: List<EventInstance>, data: DaysWidgetData.Ready, metrics: EventRowMetrics, preview: Boolean) {
    val context = LocalContext.current
    val row: @Composable (Int) -> Unit = { index ->
        val event = events[index]
        WidgetEventRow(
            event = event,
            secondary = widgetTimeLabel(context, event, data.today, data.is24Hour),
            soften = data.soften,
            metrics = metrics,
            position = positionOf(index, events.size),
            source = CalendarView.Day,
            dimmed = data.options.pastDisplay == PastEventDisplay.DIM && event.hasEnded(data.now),
        )
    }
    if (preview) {
        val fits = ((LocalSize.current.height - 12.dp + RowGap) / (metrics.rowHeight + RowGap)).toInt().coerceAtLeast(1)
        Column(modifier = GlanceModifier.fillMaxSize()) { repeat(minOf(events.size, fits)) { row(it) } }
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) { items(events.size) { row(it) } }
    }
}

/** "Nothing left today", then as many of tomorrow's first two events as the height holds. */
@Composable
private fun NothingLeft(tomorrow: List<EventInstance>, data: DaysWidgetData.Ready, metrics: EventRowMetrics) {
    val context = LocalContext.current
    val fontScale = context.resources.configuration.fontScale
    val lineHeight = 20.dp * fontScale
    val room = LocalSize.current.height - 12.dp - lineHeight
    val fits = ((room + RowGap) / (metrics.rowHeight + RowGap)).toInt().coerceIn(0, 2)
    val ahead = tomorrow.take(fits)
    val backing = GlanceModifier.wallpaperBacking(radius = 8.dp, horizontalPadding = 8.dp, filledHorizontalPadding = 4.dp)
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.widget_nothing_left_today),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = metrics.title, fontWeight = FontWeight.Medium),
            modifier = backing.height(lineHeight),
        )
        val tomorrowDate = data.today.plus(1, DateTimeUnit.DAY)
        val tomorrowLabel = relativeDayLabel(context, tomorrowDate, data.today)
        ahead.forEachIndexed { index, event ->
            WidgetEventRow(
                event = event,
                secondary = "$tomorrowLabel · ${widgetTimeLabel(context, event, tomorrowDate, data.is24Hour)}",
                soften = data.soften,
                metrics = metrics,
                position = positionOf(index, ahead.size),
                source = CalendarView.Day,
                quiet = true,
            )
        }
    }
}

/** Today's weekday and number, filled like the agenda's today pill; opens today. */
@Composable
private fun TodayPill(today: LocalDate) {
    val context = LocalContext.current
    val locale = widgetLocale()
    Column(
        modifier = GlanceModifier
            .width(PillWidth)
            .fillMaxHeight()
            .cornerRadius(PillWidth / 2)
            .background(GlanceTheme.colors.primary)
            .clickable(actionStartActivity(MainActivity.openDateIntent(context, today, CalendarView.Day)))
            .semantics { contentDescription = localizedDateFormatter(locale, "EEEEdMMMM").format(today.toJavaLocalDate()) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = today.dayOfWeek.toJavaDayOfWeek().getDisplayName(JavaTextStyle.SHORT, locale),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
        Text(
            text = today.day.toString(),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
