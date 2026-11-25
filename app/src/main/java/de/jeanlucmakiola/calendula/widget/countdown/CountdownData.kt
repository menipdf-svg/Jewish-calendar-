package de.jeanlucmakiola.calendula.widget.countdown

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import de.jeanlucmakiola.floret.glance.updateWithRefresh
import kotlinx.coroutines.CancellationException
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.widget.Themed
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.WidgetLoadContext
import de.jeanlucmakiola.calendula.widget.WidgetTicks
import de.jeanlucmakiola.calendula.widget.hasCalendarPermission
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import de.jeanlucmakiola.calendula.widget.widgetEntryPoint
import de.jeanlucmakiola.calendula.widget.widgetLoadContext
import de.jeanlucmakiola.calendula.widget.widgetState
import de.jeanlucmakiola.calendula.widget.withoutCalendars
import de.jeanlucmakiola.floret.prefs.toEnum
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private val ModeKey = stringPreferencesKey("cfg_countdown_mode")
private val EventKey = longPreferencesKey("cfg_countdown_event")
private val TitleKey = stringPreferencesKey("cfg_countdown_title")

internal val Preferences.countdownOptions: CountdownOptions
    get() = CountdownOptions(this[ModeKey].toEnum(CountdownMode.NEXT), this[EventKey], this[TitleKey])

internal fun MutablePreferences.putCountdownOptions(options: CountdownOptions) {
    this[ModeKey] = options.mode.name
    if (options.eventId != null) this[EventKey] = options.eventId else remove(EventKey)
    if (options.title != null) this[TitleKey] = options.title else remove(TitleKey)
}

/** How far ahead the next-event mode looks; past this it shows no event. */
private val NextEventHorizon = 30.days

/** How far ahead a pinned event's next occurrence is looked for. */
private val PinnedHorizon = 730.days

sealed interface CountdownData {
    data object NeedsPermission : CountdownData
    data class Ready(
        val options: CountdownOptions,
        /** The occurrence counted to; null when there is none to show. */
        val event: EventInstance?,
        val now: Instant,
        val today: LocalDate,
        val is24Hour: Boolean,
        val soften: Boolean,
        /** The placed widget, for opening its settings; null in a preview. */
        val appWidgetId: Int?,
    ) : CountdownData
}

internal suspend fun Context.loadCountdown(
    load: WidgetLoadContext,
    options: CountdownOptions,
    appWidgetId: Int?,
): Themed<CountdownData> {
    val settings = load.settings
    if (!hasCalendarPermission()) return Themed(CountdownData.NeedsPermission, settings.themeMode)
    val now = Clock.System.now()
    val repository = widgetEntryPoint().calendarRepository()
    val event = when (options.mode) {
        CountdownMode.NEXT ->
            pickNextEvent(
                repository.instances(now..now + NextEventHorizon).first().withoutCalendars(load.hiddenCalendars),
                now,
                systemZone(),
            )
        CountdownMode.PINNED -> options.eventId?.let { id ->
            pickPinnedOccurrence(repository.eventOccurrences(id, now - 1.days..now + PinnedHorizon), now, systemZone())
        }
    }
    return Themed(
        CountdownData.Ready(
            options = options,
            event = event,
            now = now,
            today = today(systemZone()),
            is24Hour = settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(this)),
            soften = settings.soften,
            appWidgetId = appWidgetId,
        ),
        settings.themeMode,
    )
}

/** [loadCountdown] for placed widget [id], arming its redraw for when the label next changes. */
internal suspend fun Context.loadPlacedCountdown(id: GlanceId): Themed<CountdownData> {
    val appWidgetId = GlanceAppWidgetManager(this).getAppWidgetId(id)
    val loaded = loadCountdown(widgetLoadContext(id), widgetState(id).countdownOptions, appWidgetId)
    val event = (loaded.content as? CountdownData.Ready)?.event
    if (event == null) {
        WidgetTicks.cancel(this, WidgetKind.COUNTDOWN, appWidgetId)
    } else {
        val zone = systemZone()
        WidgetTicks.schedule(
            this,
            WidgetKind.COUNTDOWN,
            appWidgetId,
            nextLabelChange(event.counted(zone), (loaded.content as CountdownData.Ready).now, zone),
        )
    }
    return loaded
}

/**
 * Moves countdowns pinned to [oldEventId] on to [newEventId] after the app
 * split that series, so they keep counting to the part that continues it.
 */
internal suspend fun repointCountdowns(context: Context, oldEventId: Long, newEventId: Long) {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(CountdownWidget::class.java).forEach { id ->
        try {
            var moved = false
            updateAppWidgetState(context, id) { prefs ->
                if (prefs[EventKey] == oldEventId) {
                    prefs[EventKey] = newEventId
                    moved = true
                }
            }
            if (moved) CountdownWidget().updateWithRefresh(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Gone mid-pass; nothing left to point anywhere.
        }
    }
}
