package de.jeanlucmakiola.calendula.widget.config

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetState

/** One of the app's widgets on the home screen, for the settings list. */
data class PlacedWidget(val appWidgetId: Int, val kind: WidgetKind, val appearance: WidgetAppearance)

/**
 * Every placed widget, agenda ones first, each in placement order. An id the
 * host bound but never laid out (a placement it abandoned without freeing the
 * id) has no size yet and is left out, as is one removed while this reads.
 */
suspend fun Context.placedWidgets(): List<PlacedWidget> {
    val manager = AppWidgetManager.getInstance(this) ?: return emptyList()
    val glanceManager = GlanceAppWidgetManager(this)
    return WidgetKind.entries.flatMap { kind ->
        manager.getAppWidgetIds(ComponentName(this, kind.receiver)).sorted().mapNotNull { id ->
            if (manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) <= 0) {
                return@mapNotNull null
            }
            val glanceId = runCatching { glanceManager.getGlanceIdBy(id) }.getOrNull() ?: return@mapNotNull null
            PlacedWidget(id, kind, widgetState(glanceId).widgetAppearance)
        }
    }
}
