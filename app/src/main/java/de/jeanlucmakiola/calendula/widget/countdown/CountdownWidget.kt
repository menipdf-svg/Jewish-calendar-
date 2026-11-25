package de.jeanlucmakiola.calendula.widget.countdown

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
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
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.coversWholeDays
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.widget.openIntent
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.config.WidgetConfigActivity
import de.jeanlucmakiola.calendula.widget.formatWidgetTime
import de.jeanlucmakiola.calendula.widget.relativeDayLabel
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetMessage
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.themedColor
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Countdown (#439): to the next timed event, or to a chosen event or series
 * ("Vacation · in 12 days"). Redraws when its label changes, see
 * [nextLabelChange] and [WidgetTicks]. Tapping opens the event.
 */
class CountdownWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override val previewSizeMode = previewSizeOf(WidgetKind.COUNTDOWN)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.COUNTDOWN)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadPlacedCountdown(id), refreshStamp(context, id))
        provideContent {
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadPlacedCountdown(id) }
            InAppLanguage {
                CountdownWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class CountdownWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}

@Composable
internal fun CountdownWidgetContent(data: CountdownData, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        when (data) {
            CountdownData.NeedsPermission -> WidgetMessage(context.getString(R.string.widget_needs_permission))
            is CountdownData.Ready -> {
                val event = data.event
                when {
                    event != null -> EventCountdown(event, data)
                    data.options.mode == CountdownMode.PINNED && data.options.eventId == null ->
                        Message(context.getString(R.string.widget_countdown_choose), configAction(context, data.appWidgetId))
                    data.options.mode == CountdownMode.PINNED ->
                        Message(context.getString(R.string.widget_countdown_over, data.options.title.orEmpty()), configAction(context, data.appWidgetId))
                    else -> Message(
                        context.getString(R.string.widget_upcoming_empty),
                        actionStartActivity(MainActivity.openViewIntent(context, null)),
                    )
                }
            }
        }
    }
}

private fun configAction(context: Context, appWidgetId: Int?): Action? =
    appWidgetId?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        ?.let { actionStartActivity(WidgetConfigActivity.intent(context, it)) }

@Composable
private fun Message(text: String, action: Action?) {
    val modifier = if (action != null) GlanceModifier.clickable(action) else GlanceModifier
    WidgetMessage(text, modifier = modifier)
}

@Composable
private fun EventCountdown(event: EventInstance, data: CountdownData.Ready) {
    val context = LocalContext.current
    val label = countdownLabel(event.counted(systemZone()), data.now, systemZone())
    val open = actionStartActivity(
        event.openIntent(context, CalendarView.Agenda),
    )
    // Over a wallpaper the text sits on its own card, as every other widget's rows do.
    val card = GlanceModifier.wallpaperBacking(radius = 16.dp)
    Box(modifier = GlanceModifier.fillMaxSize().padding(6.dp)) {
        Row(
            modifier = card.fillMaxSize().clickable(open).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .cornerRadius(2.dp)
                    .background(
                        themedColor(
                            day = eventAccent(event.color, dark = false, data.soften),
                            night = eventAccent(event.color, dark = true, data.soften),
                        ),
                    ),
            ) {}
            Spacer(GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = countdownText(context.resources, label),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = whenText(context, event, data),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
        }
    }
}

internal fun countdownText(res: Resources, label: CountdownLabel): String = when (label.unit) {
    CountdownUnit.NOW -> res.getString(if (label.running) R.string.widget_countdown_ending_now else R.string.widget_countdown_now)
    CountdownUnit.MINUTES -> res.getQuantityString(
        if (label.running) R.plurals.widget_countdown_ends_minutes else R.plurals.widget_countdown_in_minutes,
        label.amount,
        label.amount,
    )
    CountdownUnit.HOURS -> res.getQuantityString(
        if (label.running) R.plurals.widget_countdown_ends_hours else R.plurals.widget_countdown_in_hours,
        label.amount,
        label.amount,
    )
    CountdownUnit.DAYS -> when {
        label.amount == 1 && label.running -> res.getString(R.string.widget_countdown_ends_tomorrow)
        label.amount == 1 -> res.getString(R.string.widget_countdown_tomorrow)
        else -> res.getQuantityString(
            if (label.running) R.plurals.widget_countdown_ends_days else R.plurals.widget_countdown_in_days,
            label.amount,
            label.amount,
        )
    }
}

/** When it starts, or ends once running: "Today · 14:00", "Fri · All day". */
private fun whenText(context: Context, event: EventInstance, data: CountdownData.Ready): String {
    val counted = event.counted(systemZone())
    val running = counted.start <= data.now
    val at = if (running) counted.end else counted.start
    val date = at.toLocalDateTime(systemZone()).date
    val day = relativeDayLabel(context, date, data.today)
    val wholeDays = event.coversWholeDays(systemZone())
    return when {
        wholeDays && running -> context.getString(
            R.string.widget_countdown_until,
            relativeDayLabel(context, date.minus(1, DateTimeUnit.DAY), data.today),
        )
        wholeDays -> "$day · ${context.getString(R.string.event_detail_all_day)}"
        running -> context.getString(R.string.agenda_span_ends, "$day · ${formatWidgetTime(at, data.is24Hour)}")
        else -> "$day · ${formatWidgetTime(at, data.is24Hour)}"
    }
}
