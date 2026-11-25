package de.jeanlucmakiola.calendula.widget.agenda

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.relativeDayWord
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import de.jeanlucmakiola.calendula.domain.isCrossedOut
import de.jeanlucmakiola.calendula.widget.glanceDeclinedDecoration
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import de.jeanlucmakiola.floret.glance.WidgetHeader
import de.jeanlucmakiola.floret.glance.WidgetHeaderAction
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.anchorTodayIfMissing
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import de.jeanlucmakiola.calendula.widget.widgetEventFill
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.widget.AgendaWidgetStyle
import de.jeanlucmakiola.floret.glance.LocalWidgetBackground
import de.jeanlucmakiola.floret.glance.LocalWidgetSeeThrough
import androidx.compose.runtime.CompositionLocalProvider
import de.jeanlucmakiola.floret.glance.WidgetBackground
import de.jeanlucmakiola.floret.glance.withAlpha
import de.jeanlucmakiola.calendula.widget.widgetEventInk
import de.jeanlucmakiola.calendula.ui.common.SECONDARY_INK_ALPHA
import de.jeanlucmakiola.calendula.widget.AgendaWidgetData
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetCardColor
import de.jeanlucmakiola.calendula.widget.widgetTimeLabel
import de.jeanlucmakiola.calendula.widget.openIntent
import de.jeanlucmakiola.calendula.widget.openDayAction
import de.jeanlucmakiola.calendula.widget.loadAgendaWidget
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.glance.WidgetGroupedRow
import de.jeanlucmakiola.floret.glance.themedColor
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.prefs.ThemeMode
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek

/**
 * "Upcoming" agenda widget — a continuously scrolling list of the next ~30 days
 * of events: each day one card of grouped rows with its date pill beside it, or
 * in the classic style a label line over plain colour-stripe rows (#489).
 * Reuses the app's [groupAgendaDays] grouping so it matches the in-app agenda.
 */
class AgendaWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Single: metrics come from the chosen WidgetSize, not the measured one
    // (#103, #51), so a RemoteViews per host size bucket would only double the
    // payload. The row list stays capped below — an uncapped agenda could push
    // the RemoteViews past the binder transaction limit.
    override val sizeMode = SizeMode.Single

    override val previewSizeMode = previewSizeOf(WidgetKind.AGENDA)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.AGENDA)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stamp = refreshStamp(context, id)
        val widgetData = WidgetData(context.loadAgendaWidget(id), stamp)
        provideContent {
            // A running session never re-runs this preamble, so every redraw
            // moves the stamp and the data reloads here instead (#420).
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadAgendaWidget(id) }
            InAppLanguage {
                AgendaWidgetContent(
                    data = loaded.content,
                    appearance = currentState<Preferences>().widgetAppearance,
                    appTheme = loaded.appTheme,
                )
            }
        }
    }
}

/**
 * The whole agenda widget, shared with its config screen's preview (#292). The
 * [preview] draws its rows as a plain column, which RemoteViews can show
 * outside a widget host on every Android version.
 */
@Composable
internal fun AgendaWidgetContent(
    data: AgendaWidgetData,
    appearance: WidgetAppearance,
    appTheme: ThemeMode,
    preview: Boolean = false,
) {
    CalendulaWidgetRoot(appearance, appTheme) {
        AgendaWidgetBody(data = data, preview = preview)
    }
}

/** Rows a preview draws: Glance's cap on a Column's children, still more than a placed widget shows. */
private const val PREVIEW_ROWS = 10

/**
 * Upper bound on rows handed to the [LazyColumn], so the serialized RemoteViews
 * stays well inside the binder transaction limit regardless of range and calendar
 * size (see the [SizeMode.Single] note above). Far more than fits on screen — a
 * user scrolling a home-screen widget past a hundred rows is not a case worth
 * risking a failed update for.
 */
internal const val MAX_AGENDA_ROWS = 100

/** Flat row model: one [LazyColumn] item per event or placeholder, a day's first one carrying its date pill. */
internal sealed interface AgendaRow {
    val date: LocalDate

    data class Event(override val date: LocalDate, val event: EventInstance) : AgendaRow
    /** "Nothing left today" line for an anchored, event-less today (#35). */
    data class Placeholder(override val date: LocalDate) : AgendaRow
}

@Composable
private fun AgendaWidgetBody(data: AgendaWidgetData, preview: Boolean) {
    // The row height is re-resolved against the system font scale so it tracks the
    // sp-sized text beside it instead of drifting at large accessibility settings.
    val size = (data as? AgendaWidgetData.Ready)?.textSize ?: WidgetSize.SMALL
    val fontScale = androidx.glance.LocalContext.current.resources.configuration.fontScale
    val metrics = metricsFor(size).scaledForFont(fontScale)
    // The classic style adds no backings for a faded fill, only on a transparent one (#489).
    val classic = (data as? AgendaWidgetData.Ready)?.style == AgendaWidgetStyle.CLASSIC
    val seeThrough = if (classic) LocalWidgetBackground.current == WidgetBackground.TRANSPARENT else LocalWidgetSeeThrough.current
    CompositionLocalProvider(LocalWidgetSeeThrough provides seeThrough) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            AgendaHeader()
            Spacer(GlanceModifier.height(6.dp))
            when (data) {
                AgendaWidgetData.NeedsPermission -> WidgetMessage(R.string.widget_needs_permission, metrics)
                is AgendaWidgetData.Ready -> AgendaRows(data = data, metrics = metrics, preview = preview)
            }
        }
    }
}

@Composable
private fun AgendaRows(
    data: AgendaWidgetData.Ready,
    metrics: AgendaMetrics,
    preview: Boolean,
) {
    val pastDisplay = data.pastDisplay
    // Drop finished events (and any day they leave empty) when the mode is
    // Hide — so "Upcoming" really is. Then re-anchor today (as an empty
    // placeholder day) when the "always show today" pref is on and today has
    // no events left (#35).
    val visibleDays = anchorTodayIfMissing(
        days = data.days
            .let { days ->
                if (pastDisplay == PastEventDisplay.HIDE) {
                    days.mapNotNull { day ->
                        val remaining = day.events.filterNot { it.hasEnded(data.now) }
                        if (remaining.isEmpty()) null else day.copy(events = remaining)
                    }
                } else {
                    days
                }
            },
        today = data.today,
        enabled = data.showToday,
    )
    if (visibleDays.isEmpty()) {
        WidgetMessage(R.string.agenda_empty_title, metrics)
    } else {
        val rows = buildList {
            visibleDays.forEach { day ->
                if (day.events.isEmpty()) {
                    add(AgendaRow.Placeholder(day.date))
                } else {
                    day.events.forEach { add(AgendaRow.Event(day.date, it)) }
                }
            }
        }
        when (data.style) {
            AgendaWidgetStyle.CARDS -> CardRows(rows, data, metrics, preview)
            AgendaWidgetStyle.CLASSIC -> ClassicRows(rows, data, metrics, preview)
        }
    }
}

/** Each day is one card of grouped rows (#71), with its date pill beside the first row. */
@Composable
private fun CardRows(allRows: List<AgendaRow>, data: AgendaWidgetData.Ready, metrics: AgendaMetrics, preview: Boolean) {
    val rows = allRows.take(MAX_AGENDA_ROWS)
    val positions = dayPositions(rows.map { it.date })
    val cardColor = widgetCardColor()
    val item: @Composable (Int) -> Unit = { index ->
        val row = rows[index]
        val position = positions[index]
        val first = position == Position.Top || position == Position.Alone
        val dayRow: @Composable (GlanceModifier) -> Unit = { modifier ->
            Row(modifier = modifier.fillMaxWidth()) {
                Box(modifier = GlanceModifier.width(metrics.pillWidth)) {
                    if (first) DatePill(row.date, data.today, cardColor, metrics)
                }
                Spacer(GlanceModifier.width(GUTTER_GAP))
                Box(modifier = GlanceModifier.defaultWeight()) {
                    val rowColor = when (row) {
                        is AgendaRow.Event -> row.fill(data).let { themedColor(day = it.day, night = it.night) }
                        is AgendaRow.Placeholder -> cardColor
                    }
                    WidgetGroupedRow(position = position, color = rowColor) {
                        AgendaRowContent(row, data, metrics)
                    }
                }
            }
        }
        val gap = GlanceModifier.padding(top = if (first && index > 0) metrics.dayGap else 0.dp)
        // The pills carry no month, so a new month gets its name above its first day.
        if (first && index > 0 && row.date.month != rows[index - 1].date.month) {
            Column(modifier = gap.fillMaxWidth()) {
                MonthLabel(row.date, data.today, metrics)
                dayRow(GlanceModifier)
            }
        } else {
            dayRow(gap)
        }
    }
    RowList(rows.size, preview, item)
}

/** [count] rows of [item]: a plain column in a [preview], else a [LazyColumn]. */
@Composable
private fun RowList(count: Int, preview: Boolean, item: @Composable (Int) -> Unit) {
    if (preview) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            repeat(minOf(count, PREVIEW_ROWS)) { item(it) }
        }
    } else {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(count) { item(it) }
        }
    }
}

/** A classic list entry: a day's label line, or one of its rows. */
internal sealed interface ClassicItem {
    val date: LocalDate

    data class Header(override val date: LocalDate) : ClassicItem
    data class Entry(val row: AgendaRow) : ClassicItem {
        override val date: LocalDate get() = row.date
    }
}

/**
 * [rows] with a label before each day's first, held to [limit] items in all,
 * headers included; a label the cut leaves with nothing under it is dropped.
 */
internal fun classicItems(rows: List<AgendaRow>, limit: Int = MAX_AGENDA_ROWS): List<ClassicItem> = buildList {
    rows.forEachIndexed { index, row ->
        if (index == 0 || row.date != rows[index - 1].date) add(ClassicItem.Header(row.date))
        add(ClassicItem.Entry(row))
    }
}.take(limit).dropLastWhile { it is ClassicItem.Header }

/**
 * v2.23's look (#489): a label line per day over plain rows with a colour
 * stripe. On a transparent root each day sits on its own backing card, so the
 * text stays readable over the wallpaper; a faded fill is left to the opacity
 * and "Invert text colour" options.
 */
@Composable
private fun ClassicRows(rows: List<AgendaRow>, data: AgendaWidgetData.Ready, metrics: AgendaMetrics, preview: Boolean) {
    val items = classicItems(rows, if (preview) PREVIEW_ROWS else MAX_AGENDA_ROWS)
    val backed = LocalWidgetBackground.current == WidgetBackground.TRANSPARENT
    val ink = classicInk(inverted = data.invertText && !backed)
    val positions = if (backed) dayPositions(items.map { it.date }) else emptyList()
    val cardColor = widgetCardColor()
    val item: @Composable (Int) -> Unit = { index ->
        val entry = items[index]
        val content: @Composable () -> Unit = {
            when (entry) {
                is ClassicItem.Header -> DayHeaderRow(
                    date = entry.date,
                    today = data.today,
                    startPad = if (backed) CLASSIC_CARD_H_PAD else 8.dp,
                    topPad = when {
                        backed -> CLASSIC_CARD_TOP_PAD
                        index == 0 -> 0.dp
                        else -> metrics.dayHeaderTopPad
                    },
                    ink = ink,
                    metrics = metrics,
                )
                is ClassicItem.Entry -> when (val row = entry.row) {
                    is AgendaRow.Placeholder -> ClassicPlaceholderRow(row.date, backed, ink, metrics)
                    is AgendaRow.Event -> ClassicEventRow(row, data, backed, ink, metrics)
                }
            }
        }
        if (backed) {
            val gap = if (entry is ClassicItem.Header && index > 0) metrics.dayGap else 0.dp
            WidgetGroupedRow(
                position = positions[index],
                modifier = GlanceModifier.padding(top = gap),
                color = cardColor,
                gapBelow = false,
                content = content,
            )
        } else {
            content()
        }
    }
    RowList(items.size, preview, item)
}

/** The classic style's text colours: the theme's, or its inverse for the "Invert text colour" option. */
private data class ClassicInk(val title: ColorProvider, val secondary: ColorProvider, val today: ColorProvider)

@Composable
private fun classicInk(inverted: Boolean): ClassicInk = with(GlanceTheme.colors) {
    if (inverted) {
        ClassicInk(inverseOnSurface, inverseOnSurface.withAlpha(INVERTED_SECONDARY_INK), inversePrimary)
    } else {
        ClassicInk(onSurface, onSurfaceVariant, primary)
    }
}

/** A classic day label, "Today · Wed, 17 Jun"; opens that day in the app. */
@Composable
private fun DayHeaderRow(date: LocalDate, today: LocalDate, startPad: Dp, topPad: Dp, ink: ClassicInk, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    Text(
        text = agendaDayLabel(context, date, today),
        maxLines = 1,
        style = TextStyle(
            color = if (date == today) ink.today else ink.secondary,
            fontSize = metrics.dayHeader,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = startPad, end = 8.dp, top = topPad, bottom = metrics.rowVPad)
            .clickable(openDayAction(context, date)),
    )
}

/** "No more events today" as a muted line under an anchored, event-less today (#35), indented to the titles. */
@Composable
private fun ClassicPlaceholderRow(date: LocalDate, backed: Boolean, ink: ClassicInk, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    Text(
        text = context.getString(R.string.agenda_no_more_today),
        maxLines = 1,
        style = TextStyle(color = ink.secondary, fontSize = metrics.placeholder),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = if (backed) CLASSIC_CARD_H_PAD + STRIPE_INSET else TEXT_INDENT, end = 8.dp, top = 2.dp, bottom = metrics.rowVPad + 2.dp)
            .clickable(openDayAction(context, date)),
    )
}

/** A classic event row: the calendar's colour stripe beside the title and time; a dimmed one fades both. */
@Composable
private fun ClassicEventRow(row: AgendaRow.Event, data: AgendaWidgetData.Ready, backed: Boolean, ink: ClassicInk, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    val event = row.event
    val dimmed = row.dimmed(data)
    fun accent(dark: Boolean) = eventAccent(event.color, dark, data.soften).let {
        if (dimmed) it.copy(alpha = EventDimAlpha) else it
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = if (backed) CLASSIC_CARD_H_PAD else CLASSIC_ROW_H_PAD, vertical = metrics.rowVPad)
            .clickable(actionStartActivity(event.openIntent(context, CalendarView.Agenda))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(STRIPE_WIDTH)
                .height(metrics.contentH)
                .cornerRadius(3.dp)
                .background(themedColor(day = accent(dark = false), night = accent(dark = true))),
        ) {}
        Spacer(GlanceModifier.width(STRIPE_GAP))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                maxLines = 1,
                style = TextStyle(
                    color = if (dimmed) ink.title.withAlpha(CLASSIC_DIM_INK) else ink.title,
                    fontSize = metrics.eventTitle,
                    textDecoration = glanceDeclinedDecoration(event.isCrossedOut),
                ),
            )
            Text(
                text = eventTimeSummary(context, event, row.date, data.is24Hour),
                maxLines = 1,
                style = TextStyle(
                    color = if (dimmed) ink.secondary.withAlpha(CLASSIC_DIM_INK) else ink.secondary,
                    fontSize = metrics.eventTime,
                ),
            )
        }
    }
}

/** Space above a day's label on its own backing card, and its rows' inset, clear of the card's corners. */
private val CLASSIC_CARD_TOP_PAD = 8.dp
private val CLASSIC_CARD_H_PAD = 10.dp

/** How strongly a finished event's text shows in the classic style, on any fill. */
private const val CLASSIC_DIM_INK = 0.5f

/** The inverted secondary text's strength, as `onSurfaceVariant` sits below `onSurface`. */
private const val INVERTED_SECONDARY_INK = 0.75f

/** Where each row sits among the rows of its day, from each row's date, for the grouped-card shapes. */
private fun dayPositions(dates: List<LocalDate>): List<Position> = buildList {
    var start = 0
    for (end in 1..dates.size) {
        if (end == dates.size || dates[end] != dates[start]) {
            for (i in start until end) add(positionOf(i - start, end - start))
            start = end
        }
    }
}

@Composable
private fun AgendaRowContent(row: AgendaRow, data: AgendaWidgetData.Ready, metrics: AgendaMetrics) {
    when (row) {
        is AgendaRow.Placeholder -> PlaceholderRow(row.date, metrics)
        is AgendaRow.Event -> EventRow(
            event = row.event,
            day = row.date,
            fill = row.fill(data),
            is24Hour = data.is24Hour,
            metrics = metrics,
        )
    }
}

@Composable
private fun AgendaHeader() {
    val context = androidx.glance.LocalContext.current
    // Tap the "Upcoming" title to open the app on the user's default view
    // (issue #20) — a null target resolves to the default home in the host.
    WidgetHeader(
        title = context.getString(R.string.widget_agenda_title),
        titleAction = actionStartActivity(MainActivity.openViewIntent(context, view = null)),
        titleInset = 4.dp,
    ) {
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = context.getString(R.string.widget_new_event),
            action = actionStartActivity(MainActivity.openCreateIntent(context, today(systemZone()))),
        )
    }
}

/** The month name ("November", with its year outside the current one) above a new month's first day. */
@Composable
private fun MonthLabel(date: LocalDate, today: LocalDate, metrics: AgendaMetrics) {
    Text(
        text = monthName(date, today),
        maxLines = 1,
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = metrics.monthLabel,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .padding(bottom = 4.dp)
            .wallpaperBacking(radius = 8.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A day's weekday and number in a rounded pill, filled for today; opens that day in the app. */
@Composable
private fun DatePill(date: LocalDate, today: LocalDate, cardColor: ColorProvider, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    val isToday = date == today
    val container = if (isToday) GlanceTheme.colors.primary else cardColor
    val content = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface
    val label = agendaDayLabel(context, date, today)
    Column(
        modifier = GlanceModifier
            .width(metrics.pillWidth)
            .height(metrics.rowHeight)
            .cornerRadius(metrics.pillWidth / 2)
            .background(container)
            .clickable(openDayAction(context, date))
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = weekdayShort(date),
            maxLines = 1,
            style = TextStyle(color = content, fontSize = metrics.pillWeekday, textAlign = TextAlign.Center),
        )
        Text(
            text = date.day.toString(),
            maxLines = 1,
            style = TextStyle(
                color = content,
                fontSize = metrics.pillDay,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** "No more events today" in an anchored, event-less today's card (#35), as tall as the pill beside it. */
@Composable
private fun PlaceholderRow(date: LocalDate, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .padding(horizontal = ROW_H_PAD)
            .clickable(openDayAction(context, date)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = context.getString(R.string.agenda_no_more_today),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = metrics.placeholder),
        )
    }
}

/** An event row's light and dark fill: its calendar's container tone, faded when the row is dimmed. */
private data class EventFill(val day: Color, val night: Color, val dimmed: Boolean)

/** Whether this row is drawn faded as a finished event. */
private fun AgendaRow.Event.dimmed(data: AgendaWidgetData.Ready): Boolean =
    data.pastDisplay == PastEventDisplay.DIM && event.hasEnded(data.now)

private fun AgendaRow.Event.fill(data: AgendaWidgetData.Ready): EventFill {
    val dimmed = dimmed(data)
    return EventFill(
        day = widgetEventFill(event.color, dark = false, data.soften, dimmed),
        night = widgetEventFill(event.color, dark = true, data.soften, dimmed),
        dimmed = dimmed,
    )
}

@Composable
private fun EventRow(
    event: EventInstance,
    day: LocalDate,
    fill: EventFill,
    is24Hour: Boolean,
    metrics: AgendaMetrics,
) {
    val context = androidx.glance.LocalContext.current
    val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
    val titleInk = themedColor(day = widgetEventInk(fill.day, 1f, fill.dimmed), night = widgetEventInk(fill.night, 1f, fill.dimmed))
    val timeInk = themedColor(
        day = widgetEventInk(fill.day, SECONDARY_INK_ALPHA, fill.dimmed),
        night = widgetEventInk(fill.night, SECONDARY_INK_ALPHA, fill.dimmed),
    )
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .padding(horizontal = ROW_H_PAD)
            .clickable(actionStartActivity(event.openIntent(context, CalendarView.Agenda))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = titleInk,
                fontSize = metrics.eventTitle,
                fontWeight = FontWeight.Medium,
                textDecoration = glanceDeclinedDecoration(event.isCrossedOut),
            ),
        )
        Text(
            text = eventTimeSummary(context, event, day, is24Hour),
            maxLines = 1,
            style = TextStyle(color = timeInk, fontSize = metrics.eventTime),
        )
    }
}

@Composable
private fun WidgetMessage(resId: Int, metrics: AgendaMetrics) {
    val context = androidx.glance.LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(resId),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = metrics.message),
            modifier = GlanceModifier.wallpaperBacking().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** "Today · Wed, 17 Jun" — relative word for today/tomorrow, else the date. */
private fun agendaDayLabel(context: Context, date: LocalDate, today: LocalDate): String {
    val relative = relativeDayWord(context, date, today)
    val locale = widgetLocale()
    val java = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
    // Weekday + date in the locale's own field order (compact: no year), rather
    // than a hardcoded day-month layout.
    val formatted = localizedDateFormatter(locale, if (date.year == today.year) "EEEdMMM" else "EEEdMMMy").format(java)
    return if (relative != null) "$relative · $formatted" else formatted
}

private fun monthName(date: LocalDate, today: LocalDate): String = formatCalendarTitle(
    date = java.time.LocalDate.of(date.year, date.month.ordinal + 1, 1),
    locale = widgetLocale(),
    currentYear = today.year,
    skeleton = "LLLL",
)

private fun weekdayShort(date: LocalDate): String =
    date.dayOfWeek.toJavaDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, widgetLocale())

private fun eventTimeSummary(
    context: Context,
    event: EventInstance,
    day: LocalDate,
    is24Hour: Boolean,
): String {
    val time = widgetTimeLabel(context, event, day, is24Hour)
    val location = event.location?.takeIf { it.isNotBlank() }
    return if (location != null) "$time · $location" else time
}
