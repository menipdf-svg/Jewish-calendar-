package de.jeanlucmakiola.calendula.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.ApplicationScope
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.widget.countdown.repointCountdowns
import de.jeanlucmakiola.floret.glance.WidgetRefresher
import de.jeanlucmakiola.floret.glance.WidgetRollover
import de.jeanlucmakiola.floret.glance.updatePlaced
import de.jeanlucmakiola.floret.glance.updateWithRefresh
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Every widget receiver; the set [WidgetRollover] checks for placed instances. */
internal val WIDGET_RECEIVER_CLASSES: List<Class<*>> = WidgetKind.entries.map { it.receiver }

/** The app's own midnight wake-up (#228). */
internal const val ACTION_WIDGET_ROLLOVER = "de.jeanlucmakiola.calendula.widget.ROLLOVER"

/** Redraws every widget, for app changes they draw with that no other signal reaches, like its language (#90). */
internal const val ACTION_WIDGET_REDRAW = "de.jeanlucmakiola.calendula.widget.REDRAW"

/**
 * Asks [WidgetRedrawReceiver] to redraw every widget. A broadcast rather than a
 * coroutine, so it outlives the activity a language change recreates.
 */
fun requestWidgetRedraw(context: Context) {
    context.sendBroadcast(Intent(context, WidgetRedrawReceiver::class.java).setAction(ACTION_WIDGET_REDRAW))
}

/** The widgets a calendar change can affect. */
internal val EVENT_WIDGET_KINDS: List<WidgetKind> = WidgetKind.entries.filter { it.drawsEvents }

/** Fixed: there is only ever one rollover alarm, and re-arming must replace it. */
private const val ROLLOVER_REQUEST_CODE = 0x0DA1

internal fun widgetRolloverPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    ROLLOVER_REQUEST_CODE,
    Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_WIDGET_ROLLOVER),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

/** Arm the next midnight rollover, or cancel it when no widget is placed. Idempotent. */
internal fun syncWidgetRollover(context: Context) =
    WidgetRollover.sync(context, WIDGET_RECEIVER_CLASSES, ::widgetRolloverPendingIntent)

/**
 * Reloads and redraws every placed widget, a running session included: the
 * refresh stamp moves, and every widget keys its data load on it (#420).
 */
internal suspend fun redrawWidgets(context: Context, kinds: List<WidgetKind> = WidgetKind.entries) {
    // One kind failing, say a widget removed mid-pass, mustn't stop the others
    // or escape the broadcast's coroutine.
    kinds.forEach { kind ->
        try {
            redrawWidgets(context, kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Redrawing $kind widgets failed", e)
        }
    }
}

private const val TAG = "WidgetRedraw"

internal suspend fun redrawWidgets(context: Context, kind: WidgetKind) =
    kind.widget().updatePlaced(context, kind.receiver, bumpRefreshStamp = true)

/** Redraws only the widgets with no content options of their own, which still follow the app's. */
private suspend fun redrawUnconfiguredWidgets(context: Context) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext) ?: return
    val glanceManager = GlanceAppWidgetManager(appContext)
    WidgetKind.entries.filter { it.hasContentOptions }.forEach { kind ->
        manager.getAppWidgetIds(ComponentName(appContext, kind.receiver)).forEach { appWidgetId ->
            try {
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                if (!appContext.widgetState(glanceId).hasAgendaOptions) kind.widget().updateWithRefresh(appContext, glanceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Redrawing widget $appWidgetId failed", e)
            }
        }
    }
}

/** When the app last saw a calendar change of its own, for skipping the `PROVIDER_CHANGED` that echoes it. */
private val lastLocalChange = AtomicLong(0L)

private const val PROVIDER_ECHO_WINDOW_MILLIS = 5_000L

/** Set while a `PROVIDER_CHANGED` redraw waits out the echo window, so a burst of them redraws once. */
private val echoRedrawQueued = AtomicBoolean(false)

private fun providerEchoRemainingMillis(): Long {
    val last = lastLocalChange.get()
    if (last == 0L) return 0L
    return PROVIDER_ECHO_WINDOW_MILLIS - (SystemClock.elapsedRealtime() - last)
}

/**
 * Whether a `PROVIDER_CHANGED` should redraw. Within the echo window of one of
 * the app's own changes it may only echo that change, or be a sync landing
 * right after it, so it isn't dropped: the first one redraws once the window
 * has passed ([awaitProviderEcho]) and the rest of the burst ride along.
 */
internal fun claimProviderRedraw(): Boolean =
    providerEchoRemainingMillis() <= 0L || echoRedrawQueued.compareAndSet(false, true)

/** Waits out the echo window, if one is open, before a `PROVIDER_CHANGED` redraw. */
internal suspend fun awaitProviderEcho() {
    try {
        val wait = providerEchoRemainingMillis()
        if (wait > 0L) delay(wait)
    } finally {
        echoRedrawQueued.set(false)
    }
}

/**
 * Redraws the widgets on the app's own calendar writes, visibility changes and
 * the settings they draw with, so those don't wait for `PROVIDER_CHANGED`,
 * which arrives seconds late or not at all (#420, #229). Changes from outside
 * the app are [WidgetUpdateReceiver]'s. The app's agenda options only redraw
 * widgets that have no content options of their own (#292). Started once from
 * `CalendulaApp`.
 */
@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CalendarRepository,
    private val prefs: SettingsPrefs,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** Subscribes before returning, so a change made right after is never taken for the starting value. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val refreshers = WidgetKind.entries.associateWith { kind -> WidgetRefresher(scope) { redrawWidgets(context, kind) } }
        val all = refreshers.values
        val unconfigured = WidgetRefresher(scope) { redrawUnconfiguredWidgets(context) }
        watch(repository.localChanges) {
            lastLocalChange.set(SystemClock.elapsedRealtime())
            EVENT_WIDGET_KINDS.forEach { refreshers.getValue(it).request() }
        }
        watch(repository.seriesSplits) { split ->
            scope.launch(Dispatchers.IO) { repointCountdowns(context, split.oldEventId, split.newEventId) }
        }
        watch(prefs.widgetSettings.map { it.shared }.distinctUntilChanged().drop(1)) {
            all.forEach { it.request() }
            scope.launch(Dispatchers.IO) { publishCalendulaWidgetPreviews(context) }
        }
        watch(prefs.widgetSettings.map { it.agendaDefaults }.distinctUntilChanged().drop(1)) {
            unconfigured.request()
        }
    }

    private fun <T> watch(changes: Flow<T>, onChange: (T) -> Unit) {
        scope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            changes.collect { onChange(it) }
        }
    }
}
