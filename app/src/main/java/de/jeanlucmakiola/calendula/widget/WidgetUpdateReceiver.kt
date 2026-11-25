package de.jeanlucmakiola.calendula.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.jeanlucmakiola.floret.glance.FloretWidgetUpdateReceiver
import de.jeanlucmakiola.floret.locale.AppLanguage
import kotlin.time.Duration.Companion.minutes

/**
 * Redraws the widgets when their data, day or language goes stale, and keeps
 * the midnight rollover armed. `PROVIDER_CHANGED` covers changes made outside
 * the app, and only redraws the widgets that draw events; the app's own writes
 * go through [WidgetRefreshCoordinator]. A data change also re-arms, at most
 * every 15 minutes: the inexact alarm can be dropped in a restricted standby
 * bucket, and a calendar change is the one signal that keeps arriving. A
 * `PROVIDER_CHANGED` right after one of the app's own changes waits out the
 * echo window and redraws once ([claimProviderRedraw]).
 */
class WidgetUpdateReceiver : FloretWidgetUpdateReceiver() {
    override val rolloverAction = ACTION_WIDGET_ROLLOVER
    override val widgetReceiverClasses = WIDGET_RECEIVER_CLASSES
    override val extraHandledActions = setOf(Intent.ACTION_PROVIDER_CHANGED)
    override val extraRearmInterval = 15.minutes

    /** This broadcast's action; a receiver instance handles exactly one. */
    private var action: String? = null

    override fun rolloverPendingIntent(context: Context): PendingIntent = widgetRolloverPendingIntent(context)

    override fun skipRedraw(action: String): Boolean {
        this.action = action
        return action == Intent.ACTION_PROVIDER_CHANGED && !claimProviderRedraw()
    }

    override suspend fun redraw(context: Context) {
        val dataChange = action == Intent.ACTION_PROVIDER_CHANGED
        if (dataChange) awaitProviderEcho()
        AppLanguage.applyDefault(context)
        redrawWidgets(context, if (dataChange) EVENT_WIDGET_KINDS else WidgetKind.entries)
    }
}
