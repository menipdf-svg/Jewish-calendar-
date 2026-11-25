package de.jeanlucmakiola.calendula.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.isCrossedOut
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.SECONDARY_INK_ALPHA
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.calendula.ui.theme.CalendulaDarkFallback
import de.jeanlucmakiola.calendula.widget.agenda.metricsFor
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.glance.WidgetGroupedRow
import de.jeanlucmakiola.floret.glance.themedColor

/** How much of a past event's tone stays in its dimmed fill (#90). */
private const val WidgetDimFillAlpha = 0.3f

/** A dimmed event's ink, held above its fill. */
private const val WidgetDimInkAlpha = 0.65f

/**
 * An event's fill in the light or [dark] theme. A dimmed one is its tone faded
 * into that theme's surface, kept opaque: a translucent tint lands on the row
 * shape's white, not on the widget, and its ink would be picked for the wrong tone (#475).
 */
internal fun widgetEventFill(rawArgb: Int, dark: Boolean, soften: Boolean, dimmed: Boolean): Color {
    val tone = eventFill(rawArgb, dark, soften)
    if (!dimmed) return tone
    val surface = if (dark) CalendulaDarkFallback.surface else Color.White
    return tone.copy(alpha = WidgetDimFillAlpha).compositeOver(surface)
}

/** Ink for an event on [fill], faded when the row is [dimmed]. */
internal fun widgetEventInk(fill: Color, alpha: Float, dimmed: Boolean): Color =
    eventInk(fill, if (dimmed) alpha * WidgetDimInkAlpha else alpha)

/** Text and row sizes of a compact event list, from a widget's [WidgetSize]. */
internal data class EventRowMetrics(val title: TextUnit, val time: TextUnit, val rowHeight: Dp, val message: TextUnit)

/** The agenda widget's sizes with tighter rows, resolved against the system font scale. */
internal fun compactRowMetrics(size: WidgetSize, fontScale: Float): EventRowMetrics {
    val agenda = metricsFor(size)
    return EventRowMetrics(
        title = agenda.eventTitle,
        time = agenda.eventTime,
        rowHeight = agenda.contentH * fontScale + agenda.rowVPad,
        message = agenda.message,
    )
}

/**
 * One event as a two-line row in its calendar's fill, part of a grouped card
 * at [position]; opens the occurrence. [quiet] draws it in the card colour
 * instead, for events shown only as a look ahead.
 */
@Composable
internal fun WidgetEventRow(
    event: EventInstance,
    secondary: String,
    soften: Boolean,
    metrics: EventRowMetrics,
    position: Position,
    source: CalendarView,
    dimmed: Boolean = false,
    quiet: Boolean = false,
) {
    val context = LocalContext.current
    val day = widgetEventFill(event.color, dark = false, soften, dimmed)
    val night = widgetEventFill(event.color, dark = true, soften, dimmed)
    val card = widgetCardColor()
    WidgetGroupedRow(position = position, color = if (quiet) card else themedColor(day = day, night = night)) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(metrics.rowHeight)
                .padding(horizontal = 12.dp)
                .clickable(
                    actionStartActivity(
                        event.openIntent(context, source),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val titleInk = if (quiet) {
                GlanceTheme.colors.onSurface
            } else {
                themedColor(day = widgetEventInk(day, 1f, dimmed), night = widgetEventInk(night, 1f, dimmed))
            }
            val timeInk = if (quiet) {
                GlanceTheme.colors.onSurfaceVariant
            } else {
                themedColor(day = widgetEventInk(day, SECONDARY_INK_ALPHA, dimmed), night = widgetEventInk(night, SECONDARY_INK_ALPHA, dimmed))
            }
            Text(
                text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                maxLines = 1,
                style = TextStyle(
                    color = titleInk,
                    fontSize = metrics.title,
                    fontWeight = FontWeight.Medium,
                    textDecoration = glanceDeclinedDecoration(event.isCrossedOut),
                ),
            )
            Text(text = secondary, maxLines = 1, style = TextStyle(color = timeInk, fontSize = metrics.time))
        }
    }
}
