package de.jeanlucmakiola.calendula.ui.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.TimelineHours
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.descriptionRes
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.calendula.ui.common.icon
import de.jeanlucmakiola.calendula.ui.common.label
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.GroupedSectionHeader
import de.jeanlucmakiola.floret.components.GroupedSwitchRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.ReorderableColumn
import de.jeanlucmakiola.floret.components.ReorderableRowHeight
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.datetime.DayOfWeek
import java.time.format.TextStyle as JavaTextStyle
import kotlinx.datetime.toJavaDayOfWeek

/**
 * Views: everything that changes how a calendar view reads, grouped by the view
 * it belongs to, plus the two cross-view ordering lists (#24, #69).
 *
 * The quick-switch cycle and the drawer list are independent orders — a view
 * off in the cycle is still reachable from the drawer, including when the cycle
 * is emptied and the pill disappears (#150).
 */
@Composable
internal fun ViewsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    visible: Boolean = true,
) {
    var showMonthStyle by remember { mutableStateOf(false) }
    var showDefaultView by remember { mutableStateOf(false) }
    var showWeekStart by remember { mutableStateOf(false) }
    var showTimeFormat by remember { mutableStateOf(false) }
    var showTapOpens by remember { mutableStateOf(false) }
    var showPastEvents by remember { mutableStateOf(false) }
    var showAgendaScreenRange by remember { mutableStateOf(false) }
    var showTimelineScale by remember { mutableStateOf(false) }
    var showMultiDayCount by remember { mutableStateOf(false) }
    var showHoursStart by remember { mutableStateOf(false) }
    var showHoursEnd by remember { mutableStateOf(false) }
    val hours = state.timelineHours

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_views),
        onBack = onBack,
        predictiveBack = true,
        visible = visible,
    ) {
        val config = state.quickSwitchConfig

        // What holds for every view, above the per-view groups.
        GroupedSectionHeader(stringResource(R.string.settings_views_all_header))
        GroupedRow(
            title = stringResource(R.string.settings_default_view),
            summary = state.defaultView.label(),
            position = Position.Top,
            onClick = { showDefaultView = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_week_start),
            summary = weekStartLabel(state.weekStart),
            position = Position.Middle,
            onClick = { showWeekStart = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_time_format),
            summary = timeFormatLabel(state.timeFormat),
            position = Position.Middle,
            onClick = { showTimeFormat = true },
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_today_toolbar),
            checked = state.todayButtonInToolbar,
            onCheckedChange = viewModel::setTodayButtonInToolbar,
            summary = stringResource(R.string.settings_today_toolbar_summary),
            position = Position.Middle,
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_dim_completed),
            checked = state.dimCompletedEvents,
            onCheckedChange = viewModel::setDimCompletedEvents,
            summary = stringResource(R.string.settings_dim_completed_summary),
            position = Position.Bottom,
        )

        Spacer(Modifier.height(8.dp))

        // How events respond to a tap and a long press (#178, #141).
        GroupedSectionHeader(stringResource(R.string.settings_events_header))
        GroupedRow(
            title = stringResource(R.string.settings_tap_opens),
            summary = tapOpensLabel(state.tapOpensEditor),
            position = Position.Top,
            onClick = { showTapOpens = true },
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_drag_to_reschedule),
            checked = state.dragToReschedule,
            onCheckedChange = viewModel::setDragToReschedule,
            summary = stringResource(R.string.settings_drag_to_reschedule_summary),
            position = Position.Bottom,
        )

        Spacer(Modifier.height(8.dp))
        GroupedSectionHeader(stringResource(R.string.settings_month_header))
        GroupedRow(
            title = stringResource(R.string.settings_month_view_style),
            summary = stringResource(state.monthViewStyle.labelRes),
            position = Position.Top,
            onClick = { showMonthStyle = true },
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_week_numbers),
            checked = state.showWeekNumbers,
            onCheckedChange = viewModel::setShowWeekNumbers,
            summary = stringResource(R.string.settings_week_numbers_summary),
            position = Position.Middle,
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_month_start_times),
            checked = state.monthShowStartTimes,
            onCheckedChange = viewModel::setMonthShowStartTimes,
            summary = stringResource(R.string.settings_month_start_times_summary),
            position = Position.Bottom,
        )

        Spacer(Modifier.height(8.dp))
        GroupedSectionHeader(stringResource(R.string.settings_week_day_header))
        GroupedRow(
            title = stringResource(R.string.settings_multi_day_count),
            summary = multiDayLabel(state.multiDayCount),
            position = Position.Top,
            onClick = { showMultiDayCount = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_timeline_scale),
            summary = if (hours.enabled) {
                stringResource(R.string.settings_timeline_scale_by_visible_hours)
            } else {
                stringResource(state.timelineScale.labelRes)
            },
            position = Position.Middle,
            dimmed = hours.enabled,
            onClick = if (hours.enabled) null else ({ showTimelineScale = true }),
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_visible_hours),
            checked = hours.enabled,
            onCheckedChange = { viewModel.setTimelineHours(hours.copy(enabled = it)) },
            summary = if (hours.enabled) {
                stringResource(
                    R.string.settings_visible_hours_on_summary,
                    hourLabel(hours.startHour),
                    hourLabel(hours.endHour),
                )
            } else {
                stringResource(R.string.settings_visible_hours_summary)
            },
            position = Position.Middle,
        )
        if (hours.enabled) {
            GroupedRow(
                title = stringResource(R.string.settings_visible_hours_start),
                summary = hourLabel(hours.startHour),
                position = Position.Middle,
                onClick = { showHoursStart = true },
            )
            GroupedRow(
                title = stringResource(R.string.settings_visible_hours_end),
                summary = hourLabel(hours.endHour),
                position = Position.Middle,
                onClick = { showHoursEnd = true },
            )
        }
        GroupedSwitchRow(
            title = stringResource(R.string.settings_hour_lines),
            checked = state.showHourGrid,
            onCheckedChange = viewModel::setShowHourGrid,
            summary = stringResource(R.string.settings_hour_lines_summary),
            position = Position.Bottom,
        )

        Spacer(Modifier.height(8.dp))
        GroupedSectionHeader(stringResource(R.string.settings_agenda_header))
        GroupedRow(
            title = stringResource(R.string.settings_agenda_range),
            summary = agendaRangeLabel(state.agendaScreenRange),
            position = Position.Top,
            onClick = { showAgendaScreenRange = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_past_events),
            summary = pastEventDisplayLabel(state.pastEventDisplay),
            position = Position.Middle,
            onClick = { showPastEvents = true },
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_agenda_show_today),
            checked = state.agendaShowToday,
            onCheckedChange = viewModel::setAgendaShowToday,
            summary = stringResource(R.string.settings_agenda_show_today_hint),
            position = Position.Middle,
        )
        GroupedSwitchRow(
            title = stringResource(R.string.settings_agenda_range_bar),
            checked = state.agendaShowRangeBar,
            onCheckedChange = viewModel::setAgendaShowRangeBar,
            summary = stringResource(R.string.settings_agenda_range_bar_hint),
            position = Position.Bottom,
        )

        Spacer(Modifier.height(24.dp))
        GroupedSectionHeader(stringResource(R.string.settings_quick_switch_header))
        GroupedHint(stringResource(R.string.settings_quick_switch_hint))
        Spacer(Modifier.height(8.dp))
        ReorderableColumn(
            items = config.order,
            keyOf = { it },
            onReorder = { viewModel.setQuickSwitchOrder(it) },
        ) { view, position, dragHandle, isDragging ->
            val checked = view in config.enabled
            ViewRow(
                view = view,
                position = position,
                isDragging = isDragging,
                dragHandle = dragHandle,
                dimmed = !checked,
                trailing = {
                    Switch(
                        checked = checked,
                        onCheckedChange = { on -> viewModel.setQuickSwitchViewEnabled(view, on) },
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        GroupedSectionHeader(stringResource(R.string.settings_drawer_order_header))
        GroupedHint(stringResource(R.string.settings_drawer_order_hint))
        Spacer(Modifier.height(8.dp))
        ReorderableColumn(
            items = state.drawerViewOrder,
            keyOf = { it },
            onReorder = { viewModel.setDrawerViewOrder(it) },
        ) { view, position, dragHandle, isDragging ->
            ViewRow(
                view = view,
                position = position,
                isDragging = isDragging,
                dragHandle = dragHandle,
            )
        }
    }

    if (showMonthStyle) {
        MonthViewStylePicker(
            selected = state.monthViewStyle,
            // The preview is a real grid, so it uses the real week start too.
            weekStart = state.weekStart.resolveFirstDay(currentLocale()),
            showStartTimes = state.monthShowStartTimes,
            onSelect = viewModel::setMonthViewStyle,
            onDismiss = { showMonthStyle = false },
        )
    }
    if (showDefaultView) {
        OptionPicker(
            title = stringResource(R.string.settings_default_view),
            header = { GroupedHint(stringResource(R.string.settings_default_view_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = IMPLEMENTED_VIEWS,
            selected = state.defaultView,
            label = { it.label() },
            leading = {
                Icon(
                    imageVector = it.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onSelect = viewModel::setDefaultView,
            onDismiss = { showDefaultView = false },
        )
    }
    if (showWeekStart) {
        WeekStartPicker(
            selected = state.weekStart,
            // The preview is a real grid, so it uses the user's own month style.
            monthStyle = state.monthViewStyle,
            showStartTimes = state.monthShowStartTimes,
            options = WEEK_START_OPTIONS,
            onSelect = viewModel::setWeekStart,
            onDismiss = { showWeekStart = false },
        )
    }
    if (showTimeFormat) {
        val locale = currentLocale()
        val systemIs24Hour = DateFormat.is24HourFormat(LocalContext.current)
        OptionPicker(
            title = stringResource(R.string.settings_time_format),
            header = { GroupedHint(stringResource(R.string.settings_time_format_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = TimeFormatPref.entries,
            selected = state.timeFormat,
            label = { timeFormatLabel(it) },
            // Each option renders the same sample time its own way;
            // "Automatic" also names which of the two it resolves to.
            summary = { pref ->
                val sample = formatTimeOfDay(
                    hour = SAMPLE_HOUR,
                    minute = SAMPLE_MINUTE,
                    is24Hour = when (pref) {
                        TimeFormatPref.AUTO -> systemIs24Hour
                        TimeFormatPref.TWELVE_HOUR -> false
                        TimeFormatPref.TWENTY_FOUR_HOUR -> true
                    },
                    locale = locale,
                )
                if (pref == TimeFormatPref.AUTO) {
                    stringResource(R.string.settings_time_format_auto_summary, sample)
                } else {
                    sample
                }
            },
            onSelect = viewModel::setTimeFormat,
            onDismiss = { showTimeFormat = false },
        )
    }
    if (showTapOpens) {
        OptionPicker(
            title = stringResource(R.string.settings_tap_opens),
            header = { GroupedHint(stringResource(R.string.settings_tap_opens_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = listOf(false, true),
            selected = state.tapOpensEditor,
            label = { tapOpensLabel(it) },
            onSelect = viewModel::setTapOpensEditor,
            onDismiss = { showTapOpens = false },
        )
    }
    if (showPastEvents) {
        PastEventsPicker(
            selected = state.pastEventDisplay,
            onSelect = viewModel::setPastEventDisplay,
            onDismiss = { showPastEvents = false },
        )
    }
    if (showMultiDayCount) {
        OptionPicker(
            title = stringResource(R.string.settings_multi_day_count),
            header = { GroupedHint(stringResource(R.string.settings_multi_day_count_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = SettingsPrefs.MULTI_DAY_COUNTS.toList(),
            selected = state.multiDayCount,
            label = { multiDayLabel(it) },
            onSelect = viewModel::setMultiDayCount,
            onDismiss = { showMultiDayCount = false },
        )
    }
    if (showTimelineScale) {
        OptionPicker(
            title = stringResource(R.string.settings_timeline_scale),
            header = { GroupedHint(stringResource(R.string.settings_timeline_scale_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            // A height pinched on the timeline is listed alongside the presets
            // rather than left as a silently unticked list: it is the current
            // setting, so it has to be visible here, and seeing it next to the
            // named steps is what makes "tap one to go back" obvious.
            options = TimelineScale.presets + listOfNotNull(
                state.timelineScale as? TimelineScale.Custom,
            ),
            selected = state.timelineScale,
            label = { stringResource(it.labelRes) },
            summary = { stringResource(it.descriptionRes) },
            onSelect = viewModel::setTimelineScale,
            onDismiss = { showTimelineScale = false },
        )
    }
    if (showHoursStart) {
        OptionPicker(
            title = stringResource(R.string.settings_visible_hours_start),
            header = { GroupedHint(stringResource(R.string.settings_visible_hours_start_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = TimelineHours.startOptions(hours.endHour),
            selected = hours.startHour,
            label = { hourLabel(it) },
            onSelect = { viewModel.setTimelineHours(hours.copy(startHour = it)) },
            onDismiss = { showHoursStart = false },
        )
    }
    if (showHoursEnd) {
        OptionPicker(
            title = stringResource(R.string.settings_visible_hours_end),
            header = { GroupedHint(stringResource(R.string.settings_visible_hours_end_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = TimelineHours.endOptions(hours.startHour),
            selected = hours.endHour,
            label = { hourLabel(it) },
            onSelect = { viewModel.setTimelineHours(hours.copy(endHour = it)) },
            onDismiss = { showHoursEnd = false },
        )
    }
    if (showAgendaScreenRange) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_range),
            description = stringResource(R.string.settings_agenda_range_hint),
            selected = state.agendaScreenRange,
            weekStart = state.weekStart.resolveFirstDay(currentLocale()),
            onSelect = viewModel::setAgendaScreenRange,
            onDismiss = { showAgendaScreenRange = false },
        )
    }
}

/** Sample time the format options render: afternoon, where 12h and 24h differ. */
private const val SAMPLE_HOUR = 14
private const val SAMPLE_MINUTE = 30

/** One reorderable view row: the view's icon and name, an optional [trailing]
 *  control, and a drag handle carrying the [dragHandle] gesture modifier. */
@Composable
private fun ViewRow(
    view: CalendarView,
    position: Position,
    isDragging: Boolean,
    dragHandle: Modifier,
    dimmed: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    GroupedRow(
        title = view.label(),
        position = position,
        dimmed = dimmed,
        minHeight = ReorderableRowHeight,
        // The reorderable column owns the inter-row spacing (uniform pitch).
        gapBelow = false,
        container = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else null,
        leading = {
            Icon(
                imageVector = view.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (trailing != null) Spacer(Modifier.width(8.dp))
                Box(
                    modifier = dragHandle.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.reorder_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Picker options: "Follow system" first, then Monday…Sunday in ISO order. */
private val WEEK_START_OPTIONS: List<WeekStartPref> =
    listOf(WeekStartPref.Auto) + DayOfWeek.entries.map { WeekStartPref.Day(it) }

@Composable
internal fun weekStartLabel(pref: WeekStartPref): String = when (pref) {
    WeekStartPref.Auto -> stringResource(R.string.settings_week_start_auto)
    // Localised weekday name, so no per-day string resource is needed.
    is WeekStartPref.Day -> pref.day.toJavaDayOfWeek()
        .getDisplayName(JavaTextStyle.FULL, currentLocale())
}

@Composable
private fun tapOpensLabel(editor: Boolean): String = stringResource(
    if (editor) R.string.settings_tap_opens_editor else R.string.settings_tap_opens_details,
)

@Composable
private fun timeFormatLabel(pref: TimeFormatPref): String = stringResource(
    when (pref) {
        TimeFormatPref.AUTO -> R.string.settings_time_format_auto
        TimeFormatPref.TWELVE_HOUR -> R.string.settings_time_format_12h
        TimeFormatPref.TWENTY_FOUR_HOUR -> R.string.settings_time_format_24h
    },
)

@Composable
private fun pastEventDisplayLabel(mode: PastEventDisplay): String =
    stringResource(pastEventDisplayLabelRes(mode))

/** Shared with [PastEventsPicker], which needs the id rather than the string. */
@StringRes
internal fun pastEventDisplayLabelRes(mode: PastEventDisplay): Int = when (mode) {
    PastEventDisplay.SHOW -> R.string.settings_past_events_show
    PastEventDisplay.DIM -> R.string.settings_past_events_dim
    PastEventDisplay.HIDE -> R.string.settings_past_events_hide
}

@Composable
private fun multiDayLabel(count: Int): String =
    pluralStringResource(R.plurals.view_multi_day, count, count)

/** A whole hour in the app's clock convention; 24 reads as the end of the day. */
@Composable
private fun hourLabel(hour: Int): String =
    formatMinuteOfDay(hour * 60, LocalUse24HourFormat.current, currentLocale())
