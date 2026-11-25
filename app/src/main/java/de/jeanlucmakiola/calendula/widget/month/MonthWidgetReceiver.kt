package de.jeanlucmakiola.calendula.widget.month

import androidx.glance.appwidget.GlanceAppWidget
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver

/** Host-facing receiver for the month widget; rendering is [MonthWidget]'s. */
class MonthWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
