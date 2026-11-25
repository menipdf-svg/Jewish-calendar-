package de.jeanlucmakiola.calendula.widget.agenda

import androidx.glance.appwidget.GlanceAppWidget
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver

/** Host-facing receiver for the agenda widget; rendering is [AgendaWidget]'s. */
class AgendaWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
