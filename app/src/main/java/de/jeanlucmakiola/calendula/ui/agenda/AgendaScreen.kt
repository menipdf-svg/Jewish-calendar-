package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.calendula.ui.common.FabClearance
import de.jeanlucmakiola.calendula.ui.common.PublishChrome
import de.jeanlucmakiola.calendula.ui.common.ScreenActions
import de.jeanlucmakiola.calendula.ui.common.ScreenBody
import de.jeanlucmakiola.calendula.ui.common.ScreenChrome
import de.jeanlucmakiola.calendula.ui.common.LocalViewFocus
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.isPastOn
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.AppBarSpacing
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

// No file-level zone constant here on purpose: it would be fixed for the process
// lifetime and drift from the zone AgendaViewModel groups in after a device
// time-zone change. The zone travels on AgendaUiState.Success instead.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView> = IMPLEMENTED_VIEWS,
    modifier: Modifier = Modifier,
    restorePosition: Boolean = false,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    // Opens on today, not on the day another view was on (#367). Backing out of
    // a day opened from here, or a recreated screen, returns to the day last at
    // the top. The top day still goes back to the shared focus.
    val focus = LocalViewFocus.current
    val opened = rememberSaveable { mutableStateOf(false) }
    val restoreTop = remember(viewModel) {
        if (restorePosition || opened.value) {
            viewModel.lastTopDate
        } else {
            viewModel.goToToday()
            null
        }
    }
    SideEffect { opened.value = true }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val pastDisplay by viewModel.pastEventDisplay.collectAsStateWithLifecycle()
    val showToday by viewModel.showToday.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var showRangePicker by remember { mutableStateOf(false) }

    val isOnToday = when (val s = state) {
        is AgendaUiState.Success -> s.anchor == s.today
        else -> true
    }
    val successState = state as? AgendaUiState.Success

    // The agenda window always starts at the anchor, so moving through it is a
    // list scroll, not an anchor change — "today" has to bring the list back to
    // the top as well, and stay offered while it is scrolled away (#305).
    val listState = rememberLazyListState()
    // Past days load above the anchor (#367), so the anchor's row counts as the
    // start of the list.
    val anchorIndex = remember { mutableIntStateOf(0) }
    val scrolledAway by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex != anchorIndex.intValue ||
                listState.firstVisibleItemScrollOffset > 0
        }
    }
    val jumpToToday: () -> Unit = {
        // Off today, moving the anchor is what repositions the list; on today
        // the window doesn't change, so the scroll back is the whole action.
        if (isOnToday) scope.launch { listState.animateScrollToItem(anchorIndex.intValue) }
        viewModel.goToToday()
        (state as? AgendaUiState.Success)?.today?.let { focus?.date = it }
    }

    PublishChrome(
        ScreenChrome(
            title = stringResource(R.string.view_agenda),
            titleDate = null,
            drawerDate = anchor,
            awayFromToday = !isOnToday || (scrolledAway && successState != null),
            todayText = stringResource(R.string.agenda_today_action),
        ),
        ScreenActions(
            onToday = jumpToToday,
            onJumpToDate = { target ->
                focus?.date = target
                viewModel.goToDate(target)
            },
            onCreate = { onCreateEvent(anchor, null) },
        ),
    )

    ScreenBody(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // One bar at the top: the "showing …" header on the left and the
            // session range switcher on the right (one settings toggle).
            successState?.takeIf { it.showRangeBar }?.let { s ->
                // end lines the selector up with whatever ends the top bar:
                // the view switcher's background, or — once #150 hides it —
                // the search icon's glyph.
                val selectorEnd = if (quickSwitchViews.size >= QuickSwitchConfig.MIN_CYCLE) {
                    AppBarSpacing.Inset
                } else {
                    AppBarSpacing.IconTrailingInset
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = RANGE_BAR_TEXT_INSET,
                            end = selectorEnd,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                ) {
                    AgendaRangeBanner(
                        range = s.range,
                        start = s.anchor,
                        end = s.rangeEnd,
                        modifier = Modifier.weight(1f),
                    )
                    AgendaRangePill(
                        range = s.range,
                        isOverride = s.rangeIsOverride,
                        onClick = { showRangePicker = true },
                    )
                }
            }
            AgendaContent(
                state = state,
                pastDisplay = pastDisplay,
                showToday = showToday,
                listState = listState,
                anchorIndex = anchorIndex,
                initialTopDate = restoreTop,
                onLoadEarlier = viewModel::loadEarlier,
                onRetry = viewModel::goToToday,
                onEventClick = onEventClick,
                onOpenDay = onOpenDay,
                onTopDay = if (successState?.anchor == anchor) {
                    {
                        focus?.date = it
                        viewModel.rememberTopDay(it)
                    }
                } else null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    if (showRangePicker) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_range),
            description = stringResource(R.string.agenda_range_override_hint),
            selected = successState?.range ?: AgendaRange.Month,
            weekStart = weekStart,
            onSelect = viewModel::setRangeOverride,
            onDismiss = { showRangePicker = false },
        )
    }
}

/**
 * The agenda's current range, tapped to open the range picker as a session-only
 * override. Shares the top-bar view switcher's button shape so the two read as a
 * family, but stays low-emphasis — a subtle neutral surface tint rather than the
 * switcher's secondary container, so it doesn't compete. Fills with the primary
 * container only while an override is active, to make that temporary state clear.
 */
@Composable
private fun AgendaRangePill(
    range: AgendaRange,
    isOverride: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isOverride) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isOverride) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier,
    ) {
        Text(
            text = agendaRangeLabel(range),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Optical start inset for the range bar's text, set against the title above it. */
private val RANGE_BAR_TEXT_INSET = 28.dp

/**
 * A header naming the concrete window currently shown under a "showing …" label,
 * e.g. "27 Jun 2026" / "27 Jun – 3 Jul 2026" / "June 2026". The range's name
 * lives on the selector button beside it, so it isn't repeated here.
 */
@Composable
private fun AgendaRangeBanner(
    range: AgendaRange,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val window = agendaRangeWindowSummary(range, start, end, locale)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.agenda_range_showing_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Just the concrete dates — the range's name ("Next 30 days") already
        // sits on the selector button to the right, so repeating it here is noise.
        Text(
            text = window,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AgendaContent(
    state: AgendaUiState,
    pastDisplay: PastEventDisplay,
    showToday: Boolean,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    anchorIndex: MutableIntState = remember { mutableIntStateOf(0) },
    initialTopDate: LocalDate? = null,
    onLoadEarlier: () -> Unit = {},
    onTopDay: ((LocalDate) -> Unit)? = null,
) {
    when (state) {
        AgendaUiState.Loading -> Box(modifier)
        is AgendaUiState.Failure -> Box(modifier) {
            CalendarFailure(reason = state.reason, onRetry = onRetry)
        }
        is AgendaUiState.Success -> {
            val now by rememberCurrentMinute()
            // Hiding drops finished events — and any day they leave empty; dimming
            // keeps them but fades the row. Recomputed each minute so events fall
            // away (or fade) as they end while the screen stays open.
            val filtered = if (pastDisplay == PastEventDisplay.HIDE) {
                state.days.mapNotNull { day ->
                    val remaining = day.events.filterNot { it.isPastOn(day.date, now, state.zone) }
                    if (remaining.isEmpty()) null else day.copy(events = remaining)
                }
            } else {
                state.days
            }
            // Anchor today with a "nothing left today" placeholder — but only when
            // the agenda actually opens on today; a jumped-to date has no today
            // in it, so anchoring there would be misleading (#35).
            val days = anchorTodayIfMissing(
                days = filtered,
                today = state.today,
                enabled = showToday && state.anchor == state.today,
            )
            // Past days alone don't count as anything coming up.
            if (days.none { it.date >= state.anchor }) {
                // Nothing to scroll: drop the position a previous list left, so
                // the today FAB doesn't linger over an empty screen.
                LaunchedEffect(Unit) { listState.scrollToItem(0) }
                SideEffect { anchorIndex.intValue = 0 }
                AgendaEmpty(modifier)
            } else {
                AgendaList(
                    days = days,
                    anchor = state.anchor,
                    anchorIndex = anchorIndex,
                    initialTopDate = initialTopDate,
                    onLoadEarlier = onLoadEarlier,
                    today = state.today,
                    zone = state.zone,
                    listState = listState,
                    dimPast = pastDisplay == PastEventDisplay.DIM,
                    now = now,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onTopDay = onTopDay,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun AgendaList(
    days: List<AgendaDay>,
    anchor: LocalDate,
    anchorIndex: MutableIntState,
    initialTopDate: LocalDate?,
    onLoadEarlier: () -> Unit,
    today: LocalDate,
    zone: TimeZone,
    listState: LazyListState,
    dimPast: Boolean,
    now: Instant,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onTopDay: ((LocalDate) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val itemDates = agendaItemDates(days)
    val anchorRow = itemDates.indexOfFirst { it >= anchor }.coerceAtLeast(0)
    SideEffect { anchorIndex.intValue = anchorRow }
    // A new anchor opens on its row from the first frame; keyed on the anchor
    // the days arrived with, not the one requested. The first run may restore.
    val pendingRestore = remember { arrayOf(initialTopDate) }
    DisposableEffect(anchor) {
        val target = pendingRestore[0]
        pendingRestore[0] = null
        val index = target?.let { date -> itemDates.indexOfFirst { it >= date } }
            ?.takeIf { it >= 0 }
            ?: anchorRow
        listState.requestScrollToItem(index)
        onDispose {}
    }
    // Scrolling up near the first loaded day loads earlier days.
    val currentLoadEarlier by rememberUpdatedState(onLoadEarlier)
    val loadOnScrollUp = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && listState.firstVisibleItemIndex < LOAD_EARLIER_WITHIN) {
                    currentLoadEarlier()
                }
                return Offset.Zero
            }
        }
    }
    if (onTopDay != null) {
        val currentItemDates by rememberUpdatedState(itemDates)
        val currentOnTopDay by rememberUpdatedState(onTopDay)
        // The day the list opened on is where the view already is; only a
        // scroll moves it on.
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .drop(1)
                .collect { index -> currentItemDates.getOrNull(index)?.let(currentOnTopDay) }
        }
    }
    LazyColumn(
        modifier = modifier.nestedScroll(loadOnScrollUp),
        state = listState,
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(top = 8.dp, bottom = FabClearance),
    ) {
        agendaDays(
            days = days,
            today = today,
            zone = zone,
            // An anchored, event-less today (#35) — "nothing left today".
            emptyText = R.string.agenda_no_more_today,
            onEmptyClick = onOpenDay,
            dimmed = { event, day -> dimPast && event.isPastOn(day, now, zone) },
            onOpenDay = onOpenDay,
            onEventClick = onEventClick,
        )
    }
}

/** How close to the first loaded row an upward scroll starts loading earlier days. */
private const val LOAD_EARLIER_WITHIN = 10

/** The day of each item [AgendaList] lays out for [days], in its order. */
internal fun agendaItemDates(days: List<AgendaDay>): List<LocalDate> =
    days.flatMap { day ->
        // Header, the placeholder or the events, then the gap.
        List(2 + day.events.size.coerceAtLeast(1)) { day.date }
    }

@Composable
private fun AgendaEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Coffee,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.agenda_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
