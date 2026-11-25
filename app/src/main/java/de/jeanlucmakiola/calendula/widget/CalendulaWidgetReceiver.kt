package de.jeanlucmakiola.calendula.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import de.jeanlucmakiola.floret.glance.reloadOnUpdate
import de.jeanlucmakiola.floret.glance.restoreState

/**
 * What every widget receiver shares: the midnight rollover follows whether any
 * widget is placed (#228), a restore keeps each widget's settings (#292), and
 * the system's `updatePeriodMillis` wake-up reloads the data and re-arms the
 * alarm — anything that drops a pending alarm without a broadcast (force-stop,
 * battery restriction, an OEM freeze) is repaired there rather than on the
 * next app open.
 */
abstract class CalendulaWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        syncWidgetRollover(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        syncWidgetRollover(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        evictMonthSources(appWidgetIds)
        WidgetKind.entries.firstOrNull { it.receiver == this::class.java }?.let { kind ->
            appWidgetIds.forEach { WidgetTicks.cancel(context, kind, it) }
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        restoreState(context, oldWidgetIds, newWidgetIds)
    }

    @SuppressLint("MissingSuperCall")
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // In place of super, which only recomposes and would take the goAsync handle.
        reloadOnUpdate(context, appWidgetIds)
        syncWidgetRollover(context)
    }
}
