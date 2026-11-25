package de.jeanlucmakiola.calendula.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.glance.appwidget.GlanceAppWidgetManager
import de.jeanlucmakiola.floret.android.goAsync
import de.jeanlucmakiola.floret.glance.updateWithRefresh
import kotlinx.coroutines.CancellationException
import kotlin.time.Instant

/**
 * One alarm per placed widget, for the moment what it shows next changes: a
 * countdown's label, the Today list's last event ending. Not a wake-up alarm:
 * with the screen off nobody sees the widget, so the redraw waits for the
 * device to wake. Exact where allowed (the app holds the exact alarm
 * permission for reminders), else within a minute.
 */
internal object WidgetTicks {
    private const val ACTION_TICK = "de.jeanlucmakiola.calendula.widget.TICK"
    internal const val EXTRA_KIND = "de.jeanlucmakiola.calendula.widget.KIND"

    fun schedule(context: Context, kind: WidgetKind, appWidgetId: Int, at: Instant) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context, kind, appWidgetId)
        val millis = at.toEpochMilliseconds()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.RTC, millis, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC, millis, 60_000L, intent)
        }
    }

    fun cancel(context: Context, kind: WidgetKind, appWidgetId: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, kind, appWidgetId))
    }

    private fun pendingIntent(context: Context, kind: WidgetKind, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetTickReceiver::class.java)
                .setAction(ACTION_TICK)
                // Keys the PendingIntent per widget.
                .setData("calendula://tick/${kind.name}/$appWidgetId".toUri())
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Reloads the widget whose content just changed; the load arms its next tick. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val kind = intent.getStringExtra(WidgetTicks.EXTRA_KIND)
            ?.let { name -> WidgetKind.entries.firstOrNull { it.name == name } }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) return
        val appContext = context.applicationContext
        goAsync {
            try {
                val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                kind.widget().updateWithRefresh(appContext, glanceId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                // The widget is gone.
                WidgetTicks.cancel(appContext, kind, appWidgetId)
            } catch (_: Exception) {
                // A failed redraw leaves the last frame; the next signal retries.
            }
        }
    }
}
