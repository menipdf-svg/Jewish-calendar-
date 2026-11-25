package de.jeanlucmakiola.calendula.widget.upcoming

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.dayCount
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.DaysWidgetData
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
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import java.time.format.TextStyle as JavaTextStyle

/**
 * 2×2 upcoming (#437): today's date on top, then the next events across the
 * widget's range in compact rows, as many as its height holds. The date opens
 * the agenda, an event opens itself.
 */
class UpcomingWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Exact: how many rows show follows the height.
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.UPCOMING)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.UPCOMING)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadUpcoming(context.widgetLoadContext(id)), refreshStamp(context, id))
        provideContent {
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadUpcoming(context.widgetLoadContext(id)) }
            InAppLanguage {
                UpcomingWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class UpcomingWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

internal suspend fun Context.loadUpcoming(load: WidgetLoadContext) = loadDays(load) { today, weekStart ->
    today..today.plus(load.options.range.dayCount(today, weekStart) - 1, DateTimeUnit.DAY)
}

/** Most rows a tall widget lists; past this it is an agenda, which is its own widget. */
private const val MAX_ROWS = 8

private val RowGap = 2.dp

/** Every event still to come or running, each once, on the first day it shows. */
internal fun upcomingEvents(data: DaysWidgetData.Ready): List<Pair<LocalDate, EventInstance>> =
    data.days
        .flatMap { day -> day.events.map { day.date to it } }
        .filterNot { (_, event) -> event.hasEnded(data.now) }
        .distinctBy { (_, event) -> event.instanceId to event.start }

@Composable
internal fun UpcomingWidgetContent(data: DaysWidgetData, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        when (data) {
            DaysWidgetData.NeedsPermission -> WidgetMessage(context.getString(R.string.widget_needs_permission))
            is DaysWidgetData.Ready -> {
                val fontScale = context.resources.configuration.fontScale
                val metrics = compactRowMetrics(data.options.textSize, fontScale)
                val headerHeight = 44.dp * fontScale
                val room = LocalSize.current.height - 16.dp - headerHeight
                val fits = ((room + RowGap) / (metrics.rowHeight + RowGap)).toInt().coerceIn(1, MAX_ROWS)
                val events = upcomingEvents(data).take(fits)
                Column(modifier = GlanceModifier.fillMaxSize().padding(8.dp)) {
                    DateHeader(data.today, headerHeight)
                    if (events.isEmpty()) {
                        WidgetMessage(context.getString(R.string.widget_upcoming_empty))
                    } else {
                        events.forEachIndexed { index, (day, event) ->
                            val time = widgetTimeLabel(context, event, day, data.is24Hour)
                            WidgetEventRow(
                                event = event,
                                secondary = if (day == data.today) time else "${relativeDayLabel(context, day, data.today)} · $time",
                                soften = data.soften,
                                metrics = metrics,
                                position = positionOf(index, events.size),
                                source = CalendarView.Agenda,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Today's number beside its weekday and month; opens the agenda. */
@Composable
private fun DateHeader(today: LocalDate, height: Dp) {
    val context = LocalContext.current
    val locale = widgetLocale()
    val backing = GlanceModifier.wallpaperBacking(radius = 12.dp, horizontalPadding = 8.dp, filledHorizontalPadding = 4.dp)
    Box(modifier = GlanceModifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = backing.clickable(actionStartActivity(MainActivity.openViewIntent(context, CalendarView.Agenda))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = today.day.toString(),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    text = today.dayOfWeek.toJavaDayOfWeek().getDisplayName(JavaTextStyle.FULL, locale),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = java.time.Month.of(today.month.ordinal + 1).getDisplayName(JavaTextStyle.FULL_STANDALONE, locale),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}
