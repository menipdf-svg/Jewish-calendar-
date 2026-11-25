package de.jeanlucmakiola.calendula.widget.month

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.AndroidRemoteViews
import de.jeanlucmakiola.floret.glance.WidgetBackground
import de.jeanlucmakiola.floret.glance.LocalWidgetBackground
import androidx.glance.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.jeanlucmakiola.floret.glance.LocalWidgetThemeMode
import de.jeanlucmakiola.floret.glance.WidgetColorRole
import de.jeanlucmakiola.floret.glance.remoteColor
import de.jeanlucmakiola.floret.glance.WidgetActionStyle
import de.jeanlucmakiola.floret.glance.WidgetHeader
import de.jeanlucmakiola.floret.glance.WidgetHeaderAction
import de.jeanlucmakiola.floret.glance.WidgetHeaderActionGap
import de.jeanlucmakiola.floret.glance.WidgetHeaderTextAction
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.month.layoutMonthWeeks
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.calendula.widget.MonthWidgetSource
import de.jeanlucmakiola.calendula.widget.firstDayOfMonthIndex
import de.jeanlucmakiola.calendula.widget.loadCachedMonthSource
import de.jeanlucmakiola.calendula.widget.monthWindowCenter
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.updateWithRefresh
import de.jeanlucmakiola.calendula.widget.monthIndexOf
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import java.time.format.TextStyle as JavaTextStyle

/** Per-widget state: the displayed month as `year * 12 + monthOrdinal`. */
private val MONTH_INDEX_KEY = intPreferencesKey("month_index")

private val GRID_HPADDING = 8.dp

/**
 * Height around the week rows: body padding, the header's bottom padding and
 * spacer and the weekday pills' padding, then the header (its 28dp pills, or a title line in its transparent
 * pill when that's taller) and the weekday row, the text lines at [fontScale].
 */
private fun gridChromeHeight(fontScale: Float): Dp =
    21.dp + maxOf(28.dp, 19.dp * fontScale + 8.dp) + 15.dp * fontScale

private fun currentMonthIndex(zone: TimeZone): Int = monthIndexOf(today(zone))

private fun yearMonthOf(index: Int): YearMonth =
    firstDayOfMonthIndex(index).let { YearMonth(it.year, it.month) }

/**
 * Month-grid widget: a 6×7 calendar with today highlighted, connected multi-day
 * event bars and titled single-day pills (the in-app lane layout via
 * [layoutMonthWeeks]), and prev/next/today navigation.
 *
 * The week rows are one plain RemoteViews tree ([monthGridViews]) embedded in
 * the Glance widget, so a busy month isn't held to Glance's view-id pool (#372);
 * the lanes each week shows come from the widget's height. The displayed month lives in Glance
 * state and is read reactively in the composition ([currentState]) so the arrows
 * move it via plain recomposition, not a (here-unreliable) widget session reload.
 *
 * Everything here is written to keep the published `RemoteViews` small. A widget
 * update reaches the launcher as a *oneway* binder call, and the host's async
 * buffer is 1 MB for all of it; overrun throws `TransactionTooLargeException` and
 * the system drops the whole host, freezing every widget on the home screen until
 * the launcher rebinds. [SizeMode.Exact] serialises the grid once per size the
 * host reports, so the ceiling arrives at half the view count you would expect: a
 * grid that emitted a view per cell blew past it after three taps (#214). Every
 * row here emits only what it fills, and lanes stop at what the height shows.
 */
class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    /**
     * Exact rather than [SizeMode.Single] or [SizeMode.Responsive], both of which
     * return early from `GlanceAppWidget.resize` — under those the grid is never
     * recomposed for a new size and a resized widget clips instead of reflowing.
     * Exact costs one serialised copy per host size, so the grid draws only the
     * lanes the size can show, folding the rest into "+N".
     */
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.MONTH)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.MONTH)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val shown = getAppWidgetState<Preferences>(context, id)[MONTH_INDEX_KEY]
            ?: currentMonthIndex(systemZone())
        val stamp = refreshStamp(context, id)
        val initialCenter = monthWindowCenter(shown)
        // Shared by every size Exact composes, so each loads nothing of its own.
        val widgetData = WidgetData(context.loadCachedMonthSource(id, initialCenter, stamp), initialCenter, stamp)
        provideContent {
            val index = currentState(MONTH_INDEX_KEY) ?: currentMonthIndex(systemZone())
            // Reloads when paging leaves the loaded window, and on every redraw,
            // which a running session would otherwise never see (#420).
            val center = monthWindowCenter(index)
            val currentStamp = currentRefreshStamp()
            val loaded = widgetData.current(center, currentStamp) {
                context.loadCachedMonthSource(id, center, currentStamp)
            }
            val source = loaded.content
            // A jump out of the loaded window keeps the last month it covers
            // until the reload lands, rather than drawing an empty grid.
            var lastShown by remember { mutableIntStateOf(index) }
            val shown = when {
                source !is MonthWidgetSource.Ready || index in source.months -> index
                lastShown in source.months -> lastShown
                else -> index.coerceIn(source.months)
            }
            SideEffect { lastShown = shown }
            InAppLanguage {
                MonthWidgetContent(
                    source = source,
                    index = shown,
                    appearance = currentState<Preferences>().widgetAppearance,
                    appTheme = loaded.appTheme,
                )
            }
        }
    }
}

/** The whole month widget showing month [index], shared with its config screen's preview (#292). */
@Composable
internal fun MonthWidgetContent(source: MonthWidgetSource, index: Int, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        MonthWidgetBody(source = source, index = index)
    }
}

/**
 * Step the displayed month by the `delta` action parameter (±1).
 *
 * Redrawn through [GlanceAppWidget.update] with the id the callback was handed,
 * never `updateAll`. `updateAll` resolves its targets through the provider-name
 * -> app-widget-id map Glance persists for the *receiver*, and in a process that
 * has done nothing else yet — the one a tap wakes after a reboot — that lookup
 * comes back empty and the redraw is dropped: the state write lands, nothing
 * recomposes, and the arrows read as dead until something else opens the app and
 * starts a session (#18). The tapped widget's own id needs no lookup.
 */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val cur = prefs[MONTH_INDEX_KEY] ?: currentMonthIndex(systemZone())
            val next = cur + delta
            // Landing back on the current month clears the key, so the widget
            // goes back to *following* the date rather than being pinned to
            // whichever month was current at the tap. Paging out and back is the
            // workaround #228's reporter used, and pinning it there would have
            // stuck them on that month once it stopped being the current one.
            if (next == currentMonthIndex(systemZone())) {
                prefs.remove(MONTH_INDEX_KEY)
            } else {
                prefs[MONTH_INDEX_KEY] = next
            }
        }
        MonthWidget().update(context.applicationContext, glanceId)
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("delta")
    }
}

/** Jump the displayed month back to the current month, reloading it. See [ShiftMonthAction]. */
class ResetMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(MONTH_INDEX_KEY) }
        MonthWidget().updateWithRefresh(context.applicationContext, glanceId)
    }
}

@Composable
private fun MonthWidgetBody(source: MonthWidgetSource, index: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = GRID_HPADDING, vertical = 6.dp),
    ) {
        when (source) {
            MonthWidgetSource.NeedsPermission -> {
                MonthHeader(label = "Calendula", today = today(systemZone()))
                PermissionMessage()
            }
            is MonthWidgetSource.Ready -> {
                val zone = systemZone()
                val ym = yearMonthOf(index)
                val weeks = layoutMonthWeeks(ym, source.weekStart, source.instances, zone)
                val context = LocalContext.current
                val fontScale = context.resources.configuration.fontScale
                val weekHeight = (LocalSize.current.height - gridChromeHeight(fontScale)) / weeks.size
                val layouts = fitWeeks(weeks, lanesFitting(weekHeight, fontScale))
                val mode = LocalWidgetThemeMode.current
                val colors = GridColors(
                    onSurface = remoteColor(WidgetColorRole.ON_SURFACE),
                    onSurfaceVariant = remoteColor(WidgetColorRole.ON_SURFACE_VARIANT),
                    primary = remoteColor(WidgetColorRole.PRIMARY),
                    onPrimary = remoteColor(WidgetColorRole.ON_PRIMARY),
                    cell = remoteColor(monthCellRole()),
                )

                MonthHeader(label = monthLabel(ym, source.today.year), today = source.today)
                Spacer(GlanceModifier.height(4.dp))
                // No backing behind the grid: the weekday pills and day cells
                // each carry their own fill, so a transparent widget stays
                // readable without one blob over the wallpaper (#292).
                Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    WeekdayHeader(weekStart = source.weekStart)
                    AndroidRemoteViews(
                        remoteViews = monthGridViews(context, layouts, ym.month, source.today, source.soften, colors, mode),
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(label: String, today: LocalDate) {
    val context = LocalContext.current
    // Tap the month/year title to open the app on the month view (issue #18),
    // rooted over the default home so backing out returns there.
    WidgetHeader(
        title = label,
        titleAction = actionStartActivity(MainActivity.openViewIntent(context, CalendarView.Month)),
        modifier = GlanceModifier.padding(bottom = 2.dp),
        titleInset = 4.dp,
    ) {
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_chevron_left),
            contentDescription = context.getString(R.string.widget_prev_month),
            action = actionRunCallback<ShiftMonthAction>(actionParametersOf(ShiftMonthAction.deltaKey to -1)),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        // The "today" pill carries today's number like the app icon, and snaps
        // the grid back to the current month in place.
        WidgetHeaderTextAction(
            text = today.day.toString(),
            contentDescription = context.getString(R.string.widget_today),
            action = actionRunCallback<ResetMonthAction>(),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_chevron_right),
            contentDescription = context.getString(R.string.widget_next_month),
            action = actionRunCallback<ShiftMonthAction>(actionParametersOf(ShiftMonthAction.deltaKey to 1)),
            style = WidgetActionStyle.TONAL,
        )
        WidgetHeaderActionGap()
        WidgetHeaderAction(
            icon = ImageProvider(R.drawable.ic_widget_add),
            contentDescription = context.getString(R.string.widget_new_event),
            action = actionStartActivity(MainActivity.openCreateIntent(context, today)),
        )
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek) {
    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 1.dp)) {
        weekdayNarrowNames(weekStart).forEach { name ->
            Box(modifier = GlanceModifier.defaultWeight().padding(horizontal = 1.dp)) {
                Text(
                    text = name,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimaryContainer,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(8.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

/** The fill of a day cell: one tone off a filled root, the widget surface over a wallpaper. */
private fun monthCellRole(background: WidgetBackground): WidgetColorRole = when (background) {
    WidgetBackground.SOLID -> WidgetColorRole.SURFACE_VARIANT
    WidgetBackground.TINTED, WidgetBackground.TRANSPARENT -> WidgetColorRole.WIDGET_BACKGROUND
}

@Composable
private fun monthCellRole(): WidgetColorRole = monthCellRole(LocalWidgetBackground.current)

/** Narrow weekday initials starting at [weekStart], in the device locale.
 * Computed outside the composable so the locale read stays observable-safe. */
private fun weekdayNarrowNames(weekStart: DayOfWeek): List<String> {
    val locale = widgetLocale()
    return (0 until 7).map { i ->
        java.time.DayOfWeek.of((weekStart.ordinal + i) % 7 + 1)
            .getDisplayName(JavaTextStyle.NARROW, locale)
    }
}

@Composable
private fun PermissionMessage() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_needs_permission),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.wallpaperBacking().padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun monthLabel(month: YearMonth, currentYear: Int): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(month.year, month.month.ordinal + 1, 1),
        locale = widgetLocale(),
        currentYear = currentYear,
        skeleton = "LLLL",
    )
