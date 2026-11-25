package de.jeanlucmakiola.calendula.widget.config

import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.accountGroupTitle
import de.jeanlucmakiola.calendula.ui.common.groupByAccount
import de.jeanlucmakiola.calendula.ui.common.orUnnamed
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.GroupedSectionHeader
import de.jeanlucmakiola.floret.components.positionOf

/** Which of the app's calendars one widget shows, grouped by account like the drawer's filter. */
@Composable
internal fun WidgetCalendarPicker(
    calendars: List<CalendarSource>,
    hidden: Set<Long>,
    onSetShown: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(
        title = stringResource(R.string.widget_config_calendars),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        GroupedHint(stringResource(R.string.widget_config_calendars_hint), verticalPadding = 8.dp)
        calendars.groupByAccount().forEach { group ->
            GroupedSectionHeader(accountGroupTitle(group))
            group.calendars.forEachIndexed { index, calendar ->
                val shown = calendar.id !in hidden
                GroupedRow(
                    title = calendar.displayName.orUnnamed(),
                    position = positionOf(index, group.calendars.size),
                    minHeight = 56.dp,
                    leading = { CalendarColorChip(calendar.color) },
                    trailing = { Checkbox(checked = shown, onCheckedChange = { onSetShown(calendar.id, it) }) },
                    onClick = { onSetShown(calendar.id, !shown) },
                )
            }
        }
    }
}

/** "All calendars", or how many of them the widget shows. */
@Composable
internal fun calendarsSummary(calendars: List<CalendarSource>, hidden: Set<Long>): String {
    val shown = calendars.count { it.id !in hidden }
    return if (shown == calendars.size) {
        stringResource(R.string.widget_config_calendars_all)
    } else {
        pluralStringResource(R.plurals.widget_config_calendars_some, calendars.size, shown, calendars.size)
    }
}
