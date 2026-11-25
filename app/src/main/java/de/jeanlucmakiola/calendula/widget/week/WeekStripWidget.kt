package de.jeanlucmakiola.calendula.widget.week

import de.jeanlucmakiola.calendula.domain.isCrossedOut
import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.DaysWidgetData
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.loadDays
import de.jeanlucmakiola.calendula.widget.weekOf
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.widgetCardColor
import de.jeanlucmakiola.calendula.widget.widgetLoadContext
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetMessage
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.themedColor
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import java.time.format.TextStyle as JavaTextStyle

/**
 * 4×1 week strip (#436): the current week's seven days from the app's week
 * start, today filled, a dot per event colour under each day. A day opens
 * its day view.
 */
class WeekStripWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override val previewSizeMode = previewSizeOf(WidgetKind.WEEK_STRIP)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.WEEK_STRIP)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadWeek(id), refreshStamp(context, id))
        provideContent {
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadWeek(id) }
            InAppLanguage {
                WeekStripWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class WeekStripWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekStripWidget()
}

private suspend fun Context.loadWeek(id: GlanceId) = loadDays(widgetLoadContext(id), ::weekOf)

/** Most dots a day shows; more colours than this read as noise at this size. */
private const val MAX_DOTS = 3

private val DayGap = 4.dp
private val DotSize = 5.dp

@Composable
internal fun WeekStripWidgetContent(data: DaysWidgetData, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        when (data) {
            DaysWidgetData.NeedsPermission ->
                WidgetMessage(LocalContext.current.getString(R.string.widget_needs_permission))
            // The gap is padding on each tile rather than a Spacer between them:
            // Glance drops a row's children past the tenth, and 7 tiles plus 6
            // spacers would lose the weekend.
            is DaysWidgetData.Ready -> Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp - DayGap / 2, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = DayGap / 2)) {
                        DayTile(data.firstDay.plus(i, DateTimeUnit.DAY), data)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTile(date: LocalDate, data: DaysWidgetData.Ready) {
    val context = LocalContext.current
    val isToday = date == data.today
    val container = if (isToday) GlanceTheme.colors.primary else widgetCardColor()
    val ink = when {
        isToday -> GlanceTheme.colors.onPrimary
        date < data.today -> GlanceTheme.colors.onSurfaceVariant
        else -> GlanceTheme.colors.onSurface
    }
    val locale = widgetLocale()
    // A declined or cancelled event is no plan for the day, so it adds no dot and no count (#388).
    val events = data.eventsOn(date).filterNot { it.isCrossedOut }
    val colors = events.map { it.color }.distinct()
    val label = localizedDateFormatter(locale, "EEEEdMMMM").format(date.toJavaLocalDate())
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(14.dp)
            .background(container)
            .clickable(actionStartActivity(MainActivity.openDateIntent(context, date, CalendarView.Week)))
            .semantics {
                contentDescription = if (colors.isEmpty()) label else context.resources.getQuantityString(
                    R.plurals.widget_day_with_events, events.size, label, events.size,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.dayOfWeek.toJavaDayOfWeek().getDisplayName(JavaTextStyle.SHORT, locale),
            maxLines = 1,
            style = TextStyle(color = ink, fontSize = 11.sp, textAlign = TextAlign.Center),
        )
        Text(
            text = date.day.toString(),
            maxLines = 1,
            style = TextStyle(color = ink, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        )
        Spacer(GlanceModifier.height(3.dp))
        // A dot row is always drawn, so the numbers line up across days.
        Row(modifier = GlanceModifier.height(DotSize)) {
            colors.take(MAX_DOTS).forEachIndexed { index, color ->
                if (index > 0) Spacer(GlanceModifier.width(3.dp))
                Dot(dotColor(color, isToday, data.soften))
            }
        }
    }
}

/** A mark on the tile: the accent tone on a card, the lighter fill tone on today's primary. */
@Composable
private fun dotColor(color: Int, onPrimary: Boolean, soften: Boolean): ColorProvider = if (onPrimary) {
    themedColor(day = eventFill(color, dark = false, soften), night = eventFill(color, dark = true, soften))
} else {
    themedColor(day = eventAccent(color, dark = false, soften), night = eventAccent(color, dark = true, soften))
}

@Composable
private fun Dot(color: ColorProvider) {
    Image(
        provider = ImageProvider(R.drawable.widget_dot),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.size(DotSize),
    )
}
