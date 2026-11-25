package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.StripDrop
import de.jeanlucmakiola.calendula.ui.common.TimelineDragController
import de.jeanlucmakiola.calendula.ui.common.TimelineDrop
import de.jeanlucmakiola.calendula.ui.common.TimelineResizeDrop
import kotlinx.datetime.LocalDate

/**
 * What every timeline page hands down to its day columns: the shared vertical
 * scroll, the drag controller, and the callbacks. One instance per screen, so
 * passing it never makes a page recompose.
 */
@Stable
internal class TimelineActions(
    val scrollState: ScrollState,
    val dragController: TimelineDragController,
    val onEventClick: (EventInstance) -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
    val onCreateAt: (LocalDate, Int) -> Unit,
    val onDrop: (TimelineDrop) -> Unit,
    val onResize: (TimelineResizeDrop) -> Unit,
    val onStripDrop: (StripDrop) -> Unit,
)

/** The callbacks are read through their latest values, so the holder outlives them. */
@Composable
internal fun rememberTimelineActions(
    scrollState: ScrollState,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
    onResize: (TimelineResizeDrop) -> Unit,
    onStripDrop: (StripDrop) -> Unit,
): TimelineActions {
    val eventClick = rememberUpdatedState(onEventClick)
    val openDay = rememberUpdatedState(onOpenDay)
    val createAt = rememberUpdatedState(onCreateAt)
    val drop = rememberUpdatedState(onDrop)
    val resize = rememberUpdatedState(onResize)
    val stripDrop = rememberUpdatedState(onStripDrop)
    return remember(scrollState, dragController) {
        TimelineActions(
            scrollState = scrollState,
            dragController = dragController,
            onEventClick = { eventClick.value(it) },
            onOpenDay = { openDay.value(it) },
            onCreateAt = { date, minutes -> createAt.value(date, minutes) },
            onDrop = { drop.value(it) },
            onResize = { resize.value(it) },
            onStripDrop = { stripDrop.value(it) },
        )
    }
}
