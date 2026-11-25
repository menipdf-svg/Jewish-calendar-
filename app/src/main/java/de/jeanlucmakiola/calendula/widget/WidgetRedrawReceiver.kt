package de.jeanlucmakiola.calendula.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.jeanlucmakiola.floret.android.goAsync
import de.jeanlucmakiola.floret.locale.AppLanguage

/**
 * Takes [requestWidgetRedraw]'s in-app redraw. Not exported, unlike
 * [WidgetUpdateReceiver], so no other app can make every widget reload.
 */
class WidgetRedrawReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WIDGET_REDRAW) return
        val appContext = context.applicationContext
        goAsync {
            AppLanguage.applyDefault(appContext)
            redrawWidgets(appContext)
        }
    }
}
