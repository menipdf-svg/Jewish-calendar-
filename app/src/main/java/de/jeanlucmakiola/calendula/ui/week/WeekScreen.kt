package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import de.jeanlucmakiola.calendula.ui.common.PublishChrome
import de.jeanlucmakiola.calendula.ui.common.ScreenActions
import de.jeanlucmakiola.calendula.ui.common.ScreenBody
import de.jeanlucmakiola.calendula.ui.common.ScreenChrome
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSpec
import de.jeanlucmakiola.calendula.ui.common.rememberViewSwitchSpec
import de.jeanlucmakiola.calendula.ui.common.jumpToPage
import de.jeanlucmakiola.calendula.ui.common.pagedHeight
import de.jeanlucmakiola.floret.identity.LocalFloretDarkTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.activity.compose.BackHandler
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import de.jeanlucmakiola.calendula.ui.common.draftBlock
import de.jeanlucmakiola.calendula.ui.common.draftEvent
import de.jeanlucmakiola.calendula.ui.common.draftStartMin
import de.jeanlucmakiola.calendula.ui.common.isDraft
import de.jeanlucmakiola.calendula.ui.common.rememberDragSurface
import de.jeanlucmakiola.calendula.ui.common.rememberWriteAction
import de.jeanlucmakiola.calendula.ui.common.OutOfViewEdges
import de.jeanlucmakiola.calendula.ui.common.wallClockInstant
import de.jeanlucmakiola.calendula.ui.common.PickUp
import de.jeanlucmakiola.calendula.ui.common.SelectionBarClearance
import de.jeanlucmakiola.calendula.ui.common.ResizeEdge
import de.jeanlucmakiola.calendula.ui.common.ResizeHandle
import de.jeanlucmakiola.calendula.ui.common.ResizeHandleTarget
import de.jeanlucmakiola.calendula.ui.common.TimelineResizeDrop
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.PublishSelection
import de.jeanlucmakiola.calendula.ui.common.eventSelectAllowed
import de.jeanlucmakiola.calendula.ui.common.selectionLift
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.ui.common.LocalViewMorph
import de.jeanlucmakiola.calendula.ui.common.StayPut
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import de.jeanlucmakiola.calendula.ui.common.laneSlot
import de.jeanlucmakiola.calendula.ui.common.LaneColumn
import kotlin.math.roundToInt
import kotlin.time.Instant
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.State
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.dateZone
import de.jeanlucmakiola.calendula.domain.spanFirstDay
import de.jeanlucmakiola.calendula.domain.spanLastDay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.LocalChipGround
import de.jeanlucmakiola.calendula.ui.common.eventPaint
import de.jeanlucmakiola.calendula.ui.common.eventSurface
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.BLOCK_OUTER_INSET
import de.jeanlucmakiola.calendula.ui.common.BLOCK_TEXT_PADDING
import de.jeanlucmakiola.calendula.ui.common.BlockTimeLabel
import de.jeanlucmakiola.calendula.ui.common.BlockTitle
import de.jeanlucmakiola.calendula.ui.common.blockTextLines
import de.jeanlucmakiola.calendula.ui.common.blockTimeLines
import de.jeanlucmakiola.calendula.ui.common.rememberBlockTimeRange
import de.jeanlucmakiola.calendula.ui.common.rememberBlockTextMetrics
import de.jeanlucmakiola.calendula.ui.common.animatedBlockPlacement
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.TimelineDragController
import de.jeanlucmakiola.calendula.ui.common.ghostsDerived
import de.jeanlucmakiola.calendula.ui.common.StripDrag
import de.jeanlucmakiola.calendula.ui.common.StripDrop
import de.jeanlucmakiola.calendula.ui.common.TimelineDragOverlay
import de.jeanlucmakiola.calendula.ui.common.TimelineDrop
import de.jeanlucmakiola.calendula.ui.common.clipOffsetMinutes
import de.jeanlucmakiola.calendula.ui.common.continuesBefore
import de.jeanlucmakiola.calendula.ui.common.ChipCuts
import de.jeanlucmakiola.calendula.ui.common.timedBlockCuts
import de.jeanlucmakiola.calendula.ui.common.timedBlockShape
import de.jeanlucmakiola.calendula.ui.common.eventDragAllowed
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.eventTitleOverflow
import de.jeanlucmakiola.calendula.ui.common.rememberEventDragSource
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineDragController
import de.jeanlucmakiola.calendula.ui.common.startInstant
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.isPastOn
import de.jeanlucmakiola.calendula.ui.common.EventChipShape
import de.jeanlucmakiola.calendula.ui.common.trimmedLines
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.OutOfViewEdgeBars
import de.jeanlucmakiola.calendula.ui.common.rememberOutOfViewEdges
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.EnterOnFocus
import de.jeanlucmakiola.calendula.ui.common.LocalViewFocus
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.ViewMorphKey
import de.jeanlucmakiola.calendula.ui.common.ViewMorphPage
import de.jeanlucmakiola.calendula.ui.common.morphDay
import de.jeanlucmakiola.calendula.ui.common.morphEvent
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineScrollState
import de.jeanlucmakiola.calendula.ui.common.LocalOpeningTimeline
import de.jeanlucmakiola.calendula.ui.common.morphPageActive
import de.jeanlucmakiola.calendula.ui.common.withTitleWeight
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourGrid
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.renderedSpan
import de.jeanlucmakiola.calendula.ui.common.hourHeight
import de.jeanlucmakiola.calendula.ui.common.dayFitHeight
import de.jeanlucmakiola.calendula.ui.common.FabClearance
import de.jeanlucmakiola.calendula.ui.common.rememberTimelinePinchZoom
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.GUTTER_CONTENT_START_INSET
import de.jeanlucmakiola.calendula.ui.common.GUTTER_WIDTH
import de.jeanlucmakiola.calendula.ui.common.HourGutter
import de.jeanlucmakiola.calendula.ui.common.TIMELINE_CONTENT_END_INSET
import de.jeanlucmakiola.calendula.ui.common.hourCellBlockInset
import de.jeanlucmakiola.calendula.ui.common.hourGridCells
import de.jeanlucmakiola.calendula.ui.common.tappedMinuteOfDay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.datetime.yearMonth
import de.jeanlucmakiola.calendula.ui.common.rememberRowWeekNumber

/** One lane of the all-day strip, sized to a trimmed bar line (#190). */
private val ALL_DAY_ROW_HEIGHT = 20.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp
/** Gap between day columns; part of the column pitch a drag maps positions through. */
private val COLUMN_GAP = 2.dp

/** Breathing room between the header block and the scrolling timeline below. */
private val TIMELINE_TOP_GAP = 8.dp
private val HEADER_TOP_PADDING = 4.dp
private val HEADER_BOTTOM_PADDING = 8.dp

/** The header's date slot, reserved whether or not it holds today's circle. */
private val DATE_SLOT_SIZE = 28.dp

/** A start-aligned header cell's inset, lining its date up with the event titles below. */
private val HEADER_START_INSET = BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING

/** The weekday header's height, which the day view takes off to fit the week's hours (#184). */
@Composable
internal fun weekDayHeaderHeight(): Dp {
    val label = MaterialTheme.typography.labelSmall
    val line = label.lineHeight.takeIf { it.isSp } ?: label.fontSize
    return HEADER_TOP_PADDING + with(LocalDensity.current) { line.toDp() } + 2.dp +
        DATE_SLOT_SIZE + HEADER_BOTTOM_PADDING
}

/** Total all-day strip height for a week (0 when there are no all-day events). */
internal fun WeekUiState.Success.allDayStripHeight(): Dp {
    if (allDaySpans.isEmpty()) return 0.dp
    val lanes = allDaySpans.maxOf { it.lane } + 1
    return ALL_DAY_ROW_HEIGHT * lanes + ALL_DAY_VERTICAL_PADDING * 2
}

/**
 * The day, multi-day (#193) and week views: one timeline whose columns resize
 * in place when switching between them (#184), rather than three screens.
 */
@Composable
fun TimelineScreen(
    view: CalendarView,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    onCreateDraft: (Instant, Instant) -> Unit,
    modifier: Modifier = Modifier,
    weekViewModel: WeekViewModel = hiltViewModel(),
    multiDayViewModel: MultiDayViewModel = hiltViewModel(),
    dayViewModel: DayViewModel = hiltViewModel(),
) {
    val viewModels = remember(weekViewModel, multiDayViewModel, dayViewModel) {
        TimelineViewModels(weekViewModel, multiDayViewModel, dayViewModel)
    }
    PagedTimelineScreen(
        view = view,
        viewModels = viewModels,
        onOpenDay = onOpenDay,
        onEventClick = onEventClick,
        onCreateEvent = onCreateEvent,
        onCreateDraft = onCreateDraft,
        modifier = modifier,
    )
}

/** Each timeline view's own pages and anchor, held side by side so a switch can show both. */
internal class TimelineViewModels(
    private val week: WeekViewModel,
    private val multiDay: MultiDayViewModel,
    private val day: DayViewModel,
) {
    operator fun get(view: CalendarView): PagedTimelineViewModel = when (view) {
        CalendarView.MultiDay -> multiDay
        CalendarView.Day -> day
        else -> week
    }
}

/** What sets the timeline views apart; everything else they share. */
internal enum class TimelineKind(
    val showWeekNumber: Boolean,
    val todayText: Int,
    /** Where a header cell sits in its column: centred, or at the start like the event titles. */
    val headerBias: Float,
) {
    Week(showWeekNumber = true, todayText = R.string.week_today_action, headerBias = 0f),

    // A run of days often straddles two weeks, so no single number fits it.
    MultiDay(showWeekNumber = false, todayText = R.string.week_today_action, headerBias = 0f),
    Day(showWeekNumber = false, todayText = R.string.day_today_action, headerBias = -1f),
}

internal val CalendarView.timelineKind: TimelineKind
    get() = when (this) {
        CalendarView.MultiDay -> TimelineKind.MultiDay
        CalendarView.Day -> TimelineKind.Day
        else -> TimelineKind.Week
    }

/**
 * The zone the shown timeline is laid out in, so its blocks, drags and drops all
 * read the same wall clock (#387).
 */
internal val LocalTimelineZone = staticCompositionLocalOf { TimeZone.currentSystemDefault() }

/** How long a switch waits for the view it goes to before resizing into it without its events. */
private const val MORPH_DATA_WAIT_MS = 150L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedTimelineScreen(
    view: CalendarView,
    viewModels: TimelineViewModels,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    onCreateDraft: (Instant, Instant) -> Unit,
    modifier: Modifier,
) {
    // The view on screen, which trails [view] while the columns resize into it.
    var shown by remember { mutableStateOf(view) }
    var morph by remember { mutableStateOf<TimelineMorph?>(null) }
    // The strip height a switch ended on, for the pager it hands over to.
    var handoffStrip by remember { mutableStateOf<Dp?>(null) }
    val viewModel = viewModels[shown]
    val kind = shown.timelineKind

    // Opens on the focused date, then carries it along as the pages move.
    val focus = LocalViewFocus.current
    val navigationVersion = focus?.navigationVersion ?: 0
    var handledNavigation by remember { mutableIntStateOf(navigationVersion) }
    EnterOnFocus(viewModel) { viewModel.jumpTo(it) }
    // Keyed on the view model: collected unkeyed, a switch would read the view
    // before's anchor and grid for a frame and open its pager.
    val loadedState by key(viewModel) { viewModel.state.collectAsStateWithLifecycle() }
    // A view not shown before has nothing loaded yet; the one before it stands
    // in, as its pages load their own days anyway, rather than a skeleton.
    var lastState by remember { mutableStateOf<WeekUiState>(WeekUiState.Loading) }
    val state = if (loadedState is WeekUiState.Loading) lastState else loadedState
    SideEffect { if (loadedState !is WeekUiState.Loading) lastState = loadedState }
    LaunchedEffect(shown, loadedState) {
        if (loadedState is WeekUiState.Success) handoffStrip = null
    }
    val anchor by key(viewModel) { viewModel.anchor.collectAsStateWithLifecycle() }
    val rule by key(viewModel) { viewModel.rule.collectAsStateWithLifecycle() }
    val position = remember(anchor, rule) { positionOf(anchor, rule) }
    val grid = position.grid
    val anchorPage = position.page
    val dimCompleted by key(viewModel) { viewModel.dimCompletedEvents.collectAsStateWithLifecycle() }
    val draftMinutes by key(viewModel) { viewModel.defaultEventDuration.collectAsStateWithLifecycle() }
    // The instant before which an event counts as completed, or null when dimming
    // is off. derivedStateOf keeps the per-minute "now" from recomposing the
    // screen while the setting is off (it stays null regardless of the tick).
    val nowState = rememberCurrentMinute()
    val dimCutoff by remember(dimCompleted) {
        derivedStateOf { if (dimCompleted) nowState.value else null }
    }

    val scope = rememberCoroutineScope()

    // A new grid numbers the pages differently, so it gets a pager of its own,
    // opened straight on the anchor's page: another view, the first day of the
    // week changing, or a multi-day jump moving another date into the first column.
    val pagerState = key(viewModel, grid) {
        rememberPagerState(initialPage = anchorPage) { grid.pageCount }
    }
    val pageSpec = rememberCalendarPageSpec()
    val switchSpec = rememberViewSwitchSpec<Float>()
    val longSwitchSpec = rememberViewSwitchSpec<Float>(long = true)
    val reduceMotion = rememberReduceMotion()
    // The pager leads and the anchor follows once it settles, so this only
    // catches the anchor moving on its own. Snapped, since that is a correction
    // rather than a move.
    LaunchedEffect(pagerState, anchorPage) {
        if (pagerState.currentPage != anchorPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(anchorPage)
        }
    }
    // The page it opened on is skipped: a grid change landing in between would
    // read it as a different page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            viewModel.onPageSettled(page)
            focus?.let { it.date = grid.focusOnPage(it.date, page) }
        }
    }

    // Switching to another timeline view resizes the columns into it (#184):
    // from the page on screen to the target view's page around the focused
    // date, then hands over to that view's pager at the page the resize ended on.
    val currentShown by rememberUpdatedState(shown)
    val currentPager by rememberUpdatedState(pagerState)
    val currentGrid by rememberUpdatedState(grid)
    val currentState by rememberUpdatedState(state)
    LaunchedEffect(view, navigationVersion) {
        if (navigationVersion != handledNavigation) {
            handledNavigation = navigationVersion
            morph = null
            handoffStrip = null
            viewModels[view].jumpTo(focus?.date ?: return@LaunchedEffect)
            shown = view
            return@LaunchedEffect
        }
        // A switch interrupted by another lands first, and this one starts from there.
        snapshotFlow { morph }.first { it == null }
        withFrameNanos { }
        if (view == currentShown) return@LaunchedEffect
        val from = currentShown
        val fromGrid = currentGrid
        val fromSpan = DaySpan(fromGrid.startForPage(currentPager.currentPage), fromGrid.dayCount)
        val target = (focus?.date ?: fromSpan.start).coerceIn(fromSpan.start, fromSpan.end)
        focus?.date = target
        val toModel = viewModels[view]
        toModel.jumpTo(target)
        val to = positionOf(target, toModel.rule.value)
        val toSpan = DaySpan(to.grid.startForPage(to.page), to.grid.dayCount)
        val fromLoaded = viewModels[from].span(fromSpan)
        val toLoaded = toModel.span(toSpan)
        if (reduceMotion || currentState !is WeekUiState.Success) {
            shown = view
            return@LaunchedEffect
        }
        val switch = TimelineMorph(from.timelineKind, view.timelineKind, fromSpan, toSpan, fromLoaded, toLoaded)
        try {
            withTimeoutOrNull(MORPH_DATA_WAIT_MS) { toLoaded.first { it !is WeekUiState.Loading } }
            morph = switch
            switch.progress.animateTo(1f, if (switch.isLong) longSwitchSpec else switchSpec)
            handoffStrip = (toLoaded.value as? WeekUiState.Success)?.allDayStripHeight()
            shown = view
        } finally {
            morph = null
        }
    }

    // The page under the finger, so the title turns over mid-swipe, not after
    // it; mid-switch, the page being switched to.
    val weekStart = morph?.to?.start ?: grid.startForPage(pagerState.currentPage)
    val titleKind = morph?.toKind ?: kind
    val dayCount = morph?.to?.count ?: grid.dayCount
    val weekEnd = weekStart.plus(dayCount - 1, kotlinx.datetime.DateTimeUnit.DAY)

    // Off the ticking clock rather than the loaded page, so it turns over at
    // midnight; derived, so the per-minute tick doesn't recompose the screen.
    // The timeline's own zone, so "today" agrees with the blocks and drops (#387).
    val zone by viewModel.zone.collectAsStateWithLifecycle()
    val today by remember(zone) { derivedStateOf { nowState.value.toLocalDateTime(zone).date } }
    val isOnCurrentWeek = state !is WeekUiState.Success || (today >= weekStart && today <= weekEnd)

    // Straight to the pager: a tap mid-fling must still land, and the anchor
    // hasn't caught up with the fling yet to tell the difference. A date that
    // would open mid-page on this grid, in a view that frames its pages from the
    // date (multi-day), re-frames the pages around it instead.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        focus?.date = target
        val page = grid.pageFor(target)
        if (grid.startForPage(page) != target && viewModel.rule.value(target) != grid) {
            viewModel.jumpTo(target)
        } else {
            scope.launch { pagerState.jumpToPage(page, reduceMotion, pageSpec) }
        }
    }
    val jumpToToday = { jumpToDate(today) }

    val locale = currentLocale()
    // The day view names the month too: its header already shows weekday and date (#501).
    val (title, shortTitle) = remember(weekStart, weekEnd, locale, today.year) {
        formatTimelineTitle(weekStart, weekEnd, locale, today.year, abbreviated = false) to
            formatTimelineTitle(weekStart, weekEnd, locale, today.year, abbreviated = true)
    }
    PublishChrome(
        ScreenChrome(
            title = title,
            shortTitle = shortTitle,
            titleDate = weekStart,
            drawerDate = weekStart,
            awayFromToday = !isOnCurrentWeek,
            todayText = stringResource(titleKind.todayText),
        ),
        ScreenActions(
            onToday = jumpToToday,
            onJumpToDate = jumpToDate,
            // Anchor on today when it's in view, else the page's first day.
            onCreate = { onCreateEvent(if (isOnCurrentWeek) today else weekStart, null) },
        ),
    )

    ScreenBody(modifier = modifier) { innerPadding ->
        CompositionLocalProvider(
            LocalDimCutoff provides dimCutoff,
            LocalTimelineZone provides zone,
        ) {
            WeekContent(
                state = state,
                today = today,
                pagerState = pagerState,
                grid = grid,
                kind = kind,
                morph = morph,
                handoffStrip = handoffStrip,
                span = viewModel::span,
                onRetry = viewModel::goToToday,
                onEventClick = onEventClick,
                onOpenDay = onOpenDay,
                onCreateAt = onCreateEvent,
                onCreateDraft = onCreateDraft,
                draftMinutes = draftMinutes,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WeekContent(
    state: WeekUiState,
    today: LocalDate,
    pagerState: PagerState,
    grid: DaysGrid,
    kind: TimelineKind,
    morph: TimelineMorph?,
    handoffStrip: Dp?,
    span: (DaySpan) -> StateFlow<WeekUiState>,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onCreateDraft: (Instant, Instant) -> Unit,
    draftMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = rememberCalendarFadeSpec()

    // Shared by every page and the gutter, so the vertical scroll position
    // survives week-to-week swipes (e.g. 18:00 stays centred), and carried over
    // from the timeline view before this one.
    val stripHeight = (state as? WeekUiState.Success)?.allDayStripHeight() ?: 0.dp
    val timelineScroll = rememberTimelineScrollState(weekDayHeaderHeight() + stripHeight + TIMELINE_TOP_GAP)
    val scrollState = timelineScroll.state

    // Above the pager: a page change mid-drag would strand the floating block
    // inside the outgoing page.
    val dragController = rememberTimelineDragController()
    SideEffect { dragController.draftMinutes = draftMinutes }
    val move = LocalEventMove.current
    val zone = LocalTimelineZone.current
    val onDrop: (TimelineDrop) -> Unit = drop@{ drop ->
        val start = drop.startInstant(zone)
        val moved = drop.event.copy(start = start, end = start + (drop.event.end - drop.event.start))
        // A draft has nothing to write, so it stays selected where it landed (#153).
        if (drop.event.isDraft) {
            dragController.select(moved)
            return@drop
        }
        // A selected event stays selected where it landed (#141).
        if (dragController.selection?.event?.instanceId == drop.event.instanceId) {
            dragController.select(moved, moved = true)
        }
        move?.move(
            MoveRequest(
                eventId = drop.event.eventId,
                beginMillis = drop.event.start.toEpochMilliseconds(),
                endMillis = drop.event.end.toEpochMilliseconds(),
                isAllDay = drop.event.isAllDay,
                target = MoveTarget.Start(start),
                token = drop.token,
            ),
        )
    }

    // A resize keeps its event selected, at its new length (#141).
    val onResize: (TimelineResizeDrop) -> Unit = resize@{ drop ->
        val resized = drop.event.copy(start = drop.start, end = drop.end)
        if (drop.event.isDraft) {
            dragController.select(resized)
            return@resize
        }
        // Still lifted under its copy, so it takes over without the ghost's fade-in dip.
        dragController.select(resized, moved = true, fromLift = true)
        move?.move(
            MoveRequest(
                eventId = drop.event.eventId,
                beginMillis = drop.event.start.toEpochMilliseconds(),
                endMillis = drop.event.end.toEpochMilliseconds(),
                isAllDay = drop.event.isAllDay,
                target = MoveTarget.Span(drop.start, drop.end),
                token = drop.token,
            ),
        )
    }

    // Off the all-day strip: sideways moves days, into the grid makes it timed.
    val onStripDrop: (StripDrop) -> Unit = { drop ->
        move?.move(
            MoveRequest(
                eventId = drop.event.eventId,
                beginMillis = drop.event.start.toEpochMilliseconds(),
                endMillis = drop.event.end.toEpochMilliseconds(),
                isAllDay = drop.event.isAllDay,
                target = when (drop) {
                    is StripDrop.Days -> MoveTarget.ByDays(drop.days)
                    is StripDrop.Timed -> MoveTarget.Span(drop.start, drop.end)
                },
                token = drop.token,
            ),
        )
    }

    // A drafted block (#153) opens the new-event form at its own instants: the
    // end it is drawn to differs on a DST change (#387).
    val createDraft: (EventInstance) -> Unit = { draft ->
        dragController.deselect()
        onCreateDraft(draft.start, draft.end)
    }

    // Selection (#141) ends on back, a page or view change, or any tap.
    val selection = dragController.selection
    PublishSelection(
        event = selection?.event,
        busy = dragController.isDragging,
        compact = dragController.selectionCovered,
        onDismiss = dragController::deselect,
        onCreate = selection?.event?.takeIf { it.isDraft }?.let { draft -> { createDraft(draft) } },
        preview = { dragController.preview(zone) },
    )
    BackHandler(enabled = selection != null) { dragController.deselect() }
    LaunchedEffect(pagerState, dragController) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            // Deselecting would delete a draft mid-drag, so it stays until it lands.
            val draftInFlight = dragController.isDragging && dragController.selection?.event?.isDraft == true
            if (!draftInFlight) dragController.deselect()
        }
    }
    LaunchedEffect(morph != null) { if (morph != null) dragController.deselect() }
    LaunchedEffect(selection, dragController.settling, dragController.isDragging, dragController.selectedCandidates.size) {
        dragController.settleMovedSelection()
    }
    val orphaned = selection != null && dragController.selectedCandidates.isEmpty() &&
        !dragController.isDragging && dragController.settling == null
    LaunchedEffect(orphaned) {
        if (!orphaned) return@LaunchedEffect
        // A beat for a re-read to bring the event back under a new row.
        delay(SELECTION_ORPHAN_GRACE_MILLIS)
        dragController.deselect()
    }
    var pendingDraft by remember { mutableStateOf<EventInstance?>(null) }
    val createPendingDraft = rememberWriteAction {
        pendingDraft?.let(createDraft)
        pendingDraft = null
    }
    val openEvent: (EventInstance) -> Unit = { event ->
        if (event.isDraft) {
            pendingDraft = event
            createPendingDraft()
        } else {
            dragController.deselect()
            onEventClick(event)
        }
    }

    val actions = rememberTimelineActions(
        scrollState = scrollState,
        dragController = dragController,
        onEventClick = openEvent,
        onOpenDay = onOpenDay,
        onCreateAt = onCreateAt,
        onDrop = onDrop,
        onResize = onResize,
        onStripDrop = onStripDrop,
    )

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalOpeningTimeline provides timelineScroll) {
            // Loading and failure concern the provider as a whole, so they stand in
            // for the pager; a page still loading its own week handles that itself.
            AnimatedContent(
                targetState = state,
                modifier = Modifier.fillMaxSize(),
                contentKey = { it::class },
                transitionSpec = { fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec)) },
                label = "week-state",
            ) { s ->
                when (s) {
                    WeekUiState.Loading -> WeekLoading(dayCount = grid.dayCount)
                    is WeekUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
                    is WeekUiState.Success -> if (morph != null) {
                        TimelineMorphFrame(morph = morph, today = today, actions = actions)
                    } else WeekPager(
                        pagerState = pagerState,
                        grid = grid,
                        kind = kind,
                        span = span,
                        today = today,
                        initialStripHeight = handoffStrip ?: s.allDayStripHeight(),
                        actions = actions,
                    )
                }
            }
        }
        TimelineDragOverlay(dragController)
    }
}

/**
 * The weeks as pages that follow the finger and snap once a swipe passes half a
 * page or is flung (#131), beside a gutter that stays put.
 */
@Composable
private fun WeekPager(
    pagerState: PagerState,
    grid: DaysGrid,
    kind: TimelineKind,
    span: (DaySpan) -> StateFlow<WeekUiState>,
    today: LocalDate,
    initialStripHeight: Dp,
    actions: TimelineActions,
) {
    val dragController = actions.dragController
    val stripHeights = remember { mutableStateMapOf<Int, Dp>() }
    val allDayHeight = pagedHeight(pagerState, stripHeights, initialStripHeight)
    WeekFrame(
        weekStart = grid.startForPage(pagerState.currentPage),
        showWeekNumber = kind.showWeekNumber,
        allDayHeight = allDayHeight,
        actions = actions,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The weeks either side are composed ahead, so a swipe lands on a
            // loaded week rather than watching it fill in.
            beyondViewportPageCount = 1,
            // A held block moves within its week; the page stays put under it.
            userScrollEnabled = !dragController.isDragging,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = rememberCalendarPageSpec(),
            ),
        ) { page ->
            val pageSpan = DaySpan(grid.startForPage(page), grid.dayCount)
            val pageState by remember(pageSpan) { span(pageSpan) }.collectAsStateWithLifecycle()
            val loaded = pageState as? WeekUiState.Success
            // Until its days arrive a page shows their frame without events.
            val empty = remember(pageSpan, today) { emptySpan(pageSpan, today) }
            // A bar held over other days in the strip makes room there before it is dropped.
            val held = dragController.stripPreviews().takeIf { page == pagerState.currentPage }.orEmpty()
            val zone = LocalTimelineZone.current
            val shown = remember(loaded, held, zone) { loaded?.withStripPreview(held, zone) }
            val stripHeight = shown?.allDayStripHeight()
            DisposableEffect(page, stripHeight) {
                if (stripHeight != null) stripHeights[page] = stripHeight
                onDispose { stripHeights.remove(page) }
            }
            ViewMorphPage(active = morphPageActive(page, pagerState.settledPage)) {
                WeekPage(
                    // A cached page may predate midnight; today comes off the clock (#373).
                    state = remember(shown, empty, today) {
                        shown?.takeIf { it.today != today }?.copy(today = today) ?: shown ?: empty
                    },
                    headerBias = kind.headerBias,
                    allDayHeight = allDayHeight,
                    actions = actions,
                    active = page == pagerState.currentPage,
                )
            }
        }
    }
}

/**
 * [this] with the strip bars [held] sideways laid out at the days they are over,
 * so the bars there move aside live. Each bar keeps its own event, and with it
 * its key and the gesture on it; one moved off the page keeps a lane it is not
 * seen in.
 */
internal fun WeekUiState.Success.withStripPreview(held: List<StripDrag>, zone: TimeZone): WeekUiState.Success {
    val shifts = held.filter { !it.overGrid && it.dayShift != 0 }.associate { it.event.instanceId to it.dayShift }
    if (shifts.isEmpty()) return this
    val originals = allDaySpans.filter { it.event.instanceId in shifts }.associateBy { it.event.instanceId }
    if (originals.isEmpty()) return this
    val events = allDaySpans.map { span ->
        shifts[span.event.instanceId]?.let { span.event.shiftedDays(it, zone) } ?: span.event
    }
    val spans = layoutAllDay(events, days, zone).map { span ->
        originals[span.event.instanceId]?.let { span.copy(event = it.event) } ?: span
    }
    val laidOut = spans.mapTo(HashSet()) { it.event.instanceId }
    return copy(allDaySpans = spans + originals.values.filter { it.event.instanceId !in laidOut }.map { it.copy(lane = 0) })
}

private fun EventInstance.shiftedDays(days: Int, zone: TimeZone): EventInstance {
    val dates = dateZone(zone)
    return copy(start = start.plus(days, DateTimeUnit.DAY, dates), end = end.plus(days, DateTimeUnit.DAY, dates))
}

/** Whether these bars draw the strip drop [drop] at the days it was dropped on. */
private fun List<PlacedBar>.drawStripDrop(drop: StripDrag, zone: TimeZone): Boolean {
    val target = drop.event.spanFirstDay(zone).plus(drop.dayShift, DateTimeUnit.DAY)
    return any { bar ->
        bar.event.instanceId != drop.event.instanceId &&
            (bar.event.eventId == drop.event.eventId ||
                (drop.event.title.isNotBlank() && bar.event.title == drop.event.title)) &&
            bar.event.spanFirstDay(zone) == target
    }
}

/** A page with no events yet, for one whose days are still loading. */
private fun emptySpan(span: DaySpan, today: LocalDate): WeekUiState.Success {
    val days = (0 until span.count).map { span.start.plus(it, kotlinx.datetime.DateTimeUnit.DAY) }
    return WeekUiState.Success(
        weekStart = span.start,
        today = today,
        days = days,
        allDaySpans = emptyList(),
        timedByDay = emptyMap(),
    )
}

/**
 * The timeline mid-switch between two views (#184): every day of both pages,
 * each column between its place on the one and on the other, with no pager
 * under them. It starts and ends exactly as the two pages lay out, so the
 * pagers either side hand over without a jump.
 */
@Composable
private fun TimelineMorphFrame(
    morph: TimelineMorph,
    today: LocalDate,
    actions: TimelineActions,
) {
    val fromState by morph.fromState.collectAsStateWithLifecycle()
    val toState by morph.toState.collectAsStateWithLifecycle()
    val from = fromState as? WeekUiState.Success
    val to = toState as? WeekUiState.Success
    val geometry = remember(morph) { ColumnGeometry(morph.from, morph.to) { morph.progress.value } }
    val bars = remember(from, to) { morphBars(from, to) }
    val timedFor: (LocalDate) -> List<TimedBlock> = remember(morph, from, to) {
        { date ->
            val side = if (morph.sourceOf(date) == TimelineMorph.Side.From) from else to
            side?.timedByDay?.get(date).orEmpty()
        }
    }
    val fromStrip = from?.allDayStripHeight() ?: 0.dp
    val toStrip = to?.allDayStripHeight() ?: fromStrip
    val animatedStrip = remember(morph, fromStrip, toStrip) {
        { androidx.compose.ui.unit.lerp(fromStrip, toStrip, morph.progress.value.coerceIn(0f, 1f)) }
    }
    val fromBadge = if (morph.fromKind.showWeekNumber) 1f else 0f
    val toBadge = if (morph.toKind.showWeekNumber) 1f else 0f
    val badgeAlpha = remember(morph) {
        { lerp(fromBadge, toBadge, morph.progress.value).coerceIn(0f, 1f) }
    }
    // The header changes its alignment once; its columns and bars move in layout.
    val headerBias by remember(morph) {
        derivedStateOf {
            if (morph.progress.value < 0.5f) morph.fromKind.headerBias else morph.toKind.headerBias
        }
    }
    // The events travel inside the columns here, not between views.
    CompositionLocalProvider(LocalViewMorph provides null) {
        WeekFrame(
            weekStart = if (morph.toKind.showWeekNumber) morph.to.start else morph.from.start,
            showWeekNumber = morph.fromKind.showWeekNumber || morph.toKind.showWeekNumber,
            allDayHeight = fromStrip,
            animatedAllDayHeight = animatedStrip,
            weekNumberAlpha = badgeAlpha,
            actions = actions,
        ) {
            TimelinePageContent(
                geometry = geometry,
                today = today,
                timedFor = timedFor,
                bars = bars,
                headerBias = headerBias,
                allDayHeight = fromStrip,
                animatedAllDayHeight = animatedStrip,
                actions = actions,
                active = false,
            )
        }
    }
}

/** Read an animated strip height during measurement, without composing its contents again. */
private fun Modifier.dynamicHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val pixels = height().roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = pixels, maxHeight = pixels))
    layout(placeable.width, pixels) { placeable.place(0, 0) }
}

/** Keep a morph's expensive viewport content measured at one size while its visible edge moves. */
private fun Modifier.stableMorphViewport(stripHeight: () -> Dp, fullHeight: IntArray): Modifier =
    clipToBounds().layout { measurable, constraints ->
        if (fullHeight[0] < 0) fullHeight[0] = constraints.maxHeight + stripHeight().roundToPx()
        val measured = measurable.measure(
            constraints.copy(minHeight = fullHeight[0], maxHeight = fullHeight[0]),
        )
        layout(constraints.maxWidth, constraints.maxHeight) { measured.place(0, 0) }
    }

/**
 * What stays put while the weeks swipe: the week-number badge and the hour
 * gutter, with [pages] filling the rest.
 */
@Composable
private fun WeekFrame(
    weekStart: LocalDate,
    showWeekNumber: Boolean,
    allDayHeight: Dp,
    animatedAllDayHeight: (() -> Dp)? = null,
    weekNumberAlpha: () -> Float = { 1f },
    actions: TimelineActions,
    pages: @Composable () -> Unit,
) {
    val gutterFullHeight = remember(animatedAllDayHeight) { intArrayOf(-1) }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                WeekNumberCell(
                    weekStart = weekStart,
                    showBadge = showWeekNumber,
                    modifier = if (animatedAllDayHeight == null) Modifier
                        else Modifier.graphicsLayer { alpha = weekNumberAlpha() },
                )
                Spacer(if (animatedAllDayHeight == null) Modifier.height(allDayHeight)
                    else Modifier.dynamicHeight(animatedAllDayHeight))
            }
            Spacer(Modifier.height(TIMELINE_TOP_GAP))
            // Resolves the hour height off the same viewport height the pages'
            // timelines have, so labels and columns agree.
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().then(
                    if (animatedAllDayHeight == null) Modifier
                    else Modifier.stableMorphViewport(animatedAllDayHeight, gutterFullHeight),
                ),
            ) {
                val zoom = LocalTimelineZoom.current
                val fitHeight = dayFitHeight(
                    if (animatedAllDayHeight == null) maxHeight + allDayHeight else maxHeight,
                )
                val hourHeight = zoom.scale.hourHeight(fitHeight)
                HourGutter(
                    scrollState = actions.scrollState,
                    hourHeight = hourHeight,
                    dragController = actions.dragController,
                    modifier = rememberTimelinePinchZoom(actions.scrollState, fitHeight, hourHeight, zoom),
                )
            }
        }
        // Clipped, as the pager was: a view switch slides columns in past its edges.
        Box(modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()) { pages() }
    }
}

/** A single week in its frame, without the pager — for the Settings preview. */
@Composable
internal fun WeekSuccess(
    state: WeekUiState.Success,
    showWeekNumber: Boolean = true,
    headerBias: Float = 0f,
    allDayHeight: Dp,
    actions: TimelineActions,
) {
    WeekFrame(
        weekStart = state.weekStart,
        showWeekNumber = showWeekNumber,
        allDayHeight = allDayHeight,
        actions = actions,
    ) {
        WeekPage(
            state = state,
            headerBias = headerBias,
            allDayHeight = allDayHeight,
            actions = actions,
            active = true,
        )
    }
}

/**
 * One week's page: header, all-day strip and day columns.
 *
 * @param active whether this is the page on screen, the only one that may
 *   publish the drag geometry.
 */
@Composable
private fun WeekPage(
    state: WeekUiState.Success,
    headerBias: Float,
    allDayHeight: Dp,
    actions: TimelineActions,
    active: Boolean,
) {
    val geometry = remember(state.weekStart, state.days.size) {
        ColumnGeometry.of(DaySpan(state.weekStart, state.days.size))
    }
    val bars = remember(state) { staticBars(state) }
    TimelinePageContent(
        geometry = geometry,
        today = state.today,
        timedFor = { state.timedByDay[it].orEmpty() },
        bars = bars,
        headerBias = headerBias,
        allDayHeight = allDayHeight,
        actions = actions,
        active = active,
    )
}

/**
 * A page's header, all-day strip and day columns, laid out over [geometry]: a
 * single page's, or both pages' of a view switch mid-way (#184).
 */
@Composable
private fun TimelinePageContent(
    geometry: ColumnGeometry,
    today: LocalDate,
    timedFor: (LocalDate) -> List<TimedBlock>,
    bars: List<PlacedBar>,
    headerBias: Float,
    allDayHeight: Dp,
    animatedAllDayHeight: (() -> Dp)? = null,
    actions: TimelineActions,
    active: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            WeekDayHeader(geometry = geometry, today = today, bias = headerBias, onOpenDay = actions.onOpenDay)
            // This strip is painted on `surface`, so an outlined chip has to
            // fill with that and not with the columns' container (#230).
            CompositionLocalProvider(
                LocalChipGround provides MaterialTheme.colorScheme.surface,
            ) {
                AllDayStrip(geometry = geometry, bars = bars, height = allDayHeight,
                    animatedHeight = animatedAllDayHeight, actions = actions, active = active)
            }
        }
        Spacer(Modifier.height(TIMELINE_TOP_GAP))
        Timeline(
            geometry = geometry,
            today = today,
            timedFor = timedFor,
            allDayHeight = allDayHeight,
            animatedAllDayHeight = animatedAllDayHeight,
            actions = actions,
            active = active,
        )
    }
}

@Composable
private fun WeekDayHeader(
    geometry: ColumnGeometry,
    today: LocalDate,
    bias: Float,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    TimelineColumns(
        geometry = geometry,
        gap = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HEADER_TOP_PADDING, bottom = HEADER_BOTTOM_PADDING, end = TIMELINE_CONTENT_END_INSET),
    ) { date ->
        val javaDow = date.dayOfWeek.toJavaDayOfWeek()
        val isToday = date == today
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenDay(date) }
                .padding(start = HEADER_START_INSET * -bias),
            horizontalAlignment = BiasAlignment.Horizontal(bias),
        ) {
            // One line, as the gutter cell beside it assumes (see WeekNumberCell).
            Text(
                text = javaDow.getDisplayName(JavaTextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            // Always reserve the circle slot so the header height is
            // identical whether or not the week contains today.
            Box(
                modifier = Modifier.size(DATE_SLOT_SIZE),
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.morphDay(date).fillMaxSize(),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = date.day.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    Text(
                        text = date.day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.morphDay(date),
                    )
                }
            }
        }
    }
}

/**
 * The header's gutter cell, laid out like a day's (empty weekday line, spacer,
 * date slot) so the badge lines up with the date numbers and the cell is exactly
 * as tall as the header beside it. The start inset centres the badge on the top
 * bar's hamburger (see GUTTER_CONTENT_START_INSET).
 */
@Composable
private fun WeekNumberCell(weekStart: LocalDate, showBadge: Boolean, modifier: Modifier = Modifier) {
    val weekNumber = rememberRowWeekNumber(weekStart)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = GUTTER_CONTENT_START_INSET,
                top = HEADER_TOP_PADDING,
                bottom = HEADER_BOTTOM_PADDING,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = " ", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Box(modifier = Modifier.height(DATE_SLOT_SIZE), contentAlignment = Alignment.TopCenter) {
            if (showBadge) WeekNumberBadge(weekNumber = weekNumber)
        }
    }
}

/** Calendar-week badge shown in the header gutter, deliberately set apart with a
 *  filled box and bold number — at the month grid's size, so the two agree (#213). */
@Composable
private fun WeekNumberBadge(weekNumber: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.week_number_label)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.semantics { contentDescription = "$label $weekNumber" },
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AllDayStrip(
    geometry: ColumnGeometry,
    bars: List<PlacedBar>,
    height: Dp,
    animatedHeight: (() -> Dp)? = null,
    actions: TimelineActions,
    active: Boolean,
) {
    val dark = LocalFloretDarkTheme.current
    val zone = LocalTimelineZone.current
    val dragController = actions.dragController
    // Only the page the finger is on draws the drag; the controller is shared.
    val held = (dragController.stripDrag ?: dragController.stripSettling)?.takeIf { active }
    val sideways = held?.takeIf { !it.overGrid }
    val lifted = dragController.liftedInstanceId?.takeIf { active && dragController.stripDrag?.overGrid != false }
    val settling = dragController.stripSettling?.takeIf { active }
    val heldStrips = dragController.heldStrips.takeIf { active }.orEmpty()
    // Seated once the re-read draws the event at the days it was dropped on.
    LaunchedEffect(settling, heldStrips, bars) {
        if (settling != null && bars.drawStripDrop(settling, zone)) dragController.seatStrip()
        heldStrips.forEach { if (!it.seated && bars.drawStripDrop(it.drag, zone)) dragController.seatHeldStrip(it.token) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Height is hoisted + animated so it slides and resizes smoothly;
            // padding sits inside it so the content area is lanes * row height.
            .then(if (animatedHeight == null) Modifier.height(height) else Modifier.dynamicHeight(animatedHeight))
            .padding(
                top = ALL_DAY_VERTICAL_PADDING,
                bottom = ALL_DAY_VERTICAL_PADDING,
                end = TIMELINE_CONTENT_END_INSET,
            ),
    ) {
        // Span bars are positioned absolutely so a multi-day event is one
        // connected bar across columns rather than a chip per day. clipToBounds
        // keeps bars from spilling out while the height animates.
        Layout(
            content = {
                bars.forEach { bar ->
                    key(bar.event.eventId, bar.event.start) {
                        val moving = sideways?.event?.instanceId == bar.event.instanceId
                        val lane = animatedStripLane(bar.toLane)
                        AllDayBar(
                            event = bar.event,
                            lastDay = bar.toRange.endInclusive,
                            dark = dark,
                            dragController = dragController,
                            lifted = moving,
                            ghosted = lifted == bar.event.instanceId,
                            onClick = { actions.onEventClick(bar.event) },
                            onDrop = actions.onStripDrop,
                            modifier = Modifier
                                .then(if (moving) Modifier.zIndex(1f) else Modifier)
                                .then(StripLane(lane))
                                .morphEvent(ViewMorphKey.event(bar.event, bar.keyDate))
                                .padding(horizontal = 1.dp, vertical = 1.dp),
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) { measurables, constraints ->
            val width = constraints.maxWidth.toFloat()
            val rowPx = ALL_DAY_ROW_HEIGHT.toPx()
            val progress = geometry.progress()
            val placed = measurables.mapIndexed { i, measurable ->
                val bar = bars[i]
                val shift = sideways?.takeIf { it.event.instanceId == bar.event.instanceId }?.dayShift ?: 0
                val extent = if (shift == 0) {
                    geometry.extent(bar.fromRange, bar.toRange, width, 0f)
                } else {
                    // The whole event, not the part this page shows: it may come in from off screen.
                    val moved = bar.event.spanFirstDay(zone)..bar.event.spanLastDay(zone)
                    geometry.extent(moved.shiftedBy(shift), moved.shiftedBy(shift), width, 0f)
                }
                val left = extent.left.roundToInt()
                val barWidth = (extent.right.roundToInt() - left).coerceAtLeast(0)
                // Standing still a bar tweens to a new lane, as bars make room for a dragged one.
                val lane = (measurable.parentData as? StripLane)?.lane?.takeIf { bar.fromLane == bar.toLane }
                val y = ((lane?.value ?: lerp(bar.fromLane.toFloat(), bar.toLane.toFloat(), progress)) * rowPx).roundToInt()
                Triple(measurable.measure(Constraints.fixed(barWidth, rowPx.roundToInt())), left, y)
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placed.forEach { (placeable, x, y) -> placeable.placeRelative(x, y) }
            }
        }
    }
}

private data class StripLane(val lane: State<Float>) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@StripLane
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun animatedStripLane(lane: Int): State<Float> {
    val spec: FiniteAnimationSpec<Float> =
        if (rememberReduceMotion()) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    return animateFloatAsState(lane.toFloat(), spec, label = "strip-lane")
}

private fun ClosedRange<LocalDate>.shiftedBy(days: Int): ClosedRange<LocalDate> =
    start.plus(days, DateTimeUnit.DAY)..endInclusive.plus(days, DateTimeUnit.DAY)

@Composable
private fun AllDayBar(
    event: EventInstance,
    lastDay: LocalDate,
    dark: Boolean,
    dragController: TimelineDragController,
    lifted: Boolean,
    ghosted: Boolean,
    onClick: () -> Unit,
    onDrop: (StripDrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    val dimCutoff = LocalDimCutoff.current
    // A bar is one piece, so it dims once the last day it covers here has passed.
    val dimmed = dimCutoff != null && event.isPastOn(lastDay, dimCutoff)
    val paint = eventPaint(event, dark)
    val zone = LocalTimelineZone.current
    val draggable = eventDragAllowed(event)
    // Long press lifts the bar: sideways moves its days, down into the grid makes it timed.
    val dragModifier = rememberEventDragSource(
        enabled = draggable,
        key = event.instanceId,
        onPickUp = { pointer, _, _ ->
            dragController.beginStrip(event, zone, pointer)
            if (dragController.stripDrag != null) PickUp.Drag else PickUp.None
        },
        onMove = dragController::move,
        onDrop = { dragController.finishStrip()?.let(onDrop) },
        onCancel = dragController::cancel,
        onRelease = dragController::cancel,
    )
    // Ghosted while it hangs over the grid as a timed block, and until that drop lands.
    val ghost = ghostAlpha(ghosted)
    val raise = selectionLift(lifted)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .alpha(ghost)
            .selectionLift(raise, EventChipShape)
            .then(dragModifier)
            .eventSurface(paint, EventChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        val titleOverflow = eventTitleOverflow()
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.trimmedLines(),
            maxLines = 1,
            overflow = titleOverflow.overflow,
            softWrap = titleOverflow.softWrap,
            color = paint.titleInk,
            fontWeight = paint.titleWeight,
            textDecoration = paint.decoration,
        )
    }
}

@Composable
private fun Timeline(
    geometry: ColumnGeometry,
    today: LocalDate,
    timedFor: (LocalDate) -> List<TimedBlock>,
    allDayHeight: Dp,
    animatedAllDayHeight: (() -> Dp)? = null,
    actions: TimelineActions,
    active: Boolean,
) {
    val scrollState = actions.scrollState
    val dragController = actions.dragController
    val fullViewportHeight = remember(geometry) { intArrayOf(-1) }
    val dark = LocalFloretDarkTheme.current
    val zoom = LocalTimelineZoom.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // What the hour grid takes off a block's ends, for the floating copy to
    // take off its own. Per-end, a cut edge keeps it — that much the drag
    // geometry decides itself, from the slice it is drawing.
    val blockInsetPx = with(density) {
        hourCellBlockInset(LocalShowHourGrid.current, cut = false).toPx()
    }

    // BoxWithConstraints rather than Box: the fit-the-whole-day scale needs the
    // timeline's own viewport height, which is only known here — below the top
    // bar, day header and all-day strip. The strip is given back, so the scale
    // holds while paging and matches the day view's (#184).
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().then(
            if (animatedAllDayHeight == null) Modifier
            else Modifier.stableMorphViewport(animatedAllDayHeight, fullViewportHeight),
        ),
    ) {
        val fitHeight = dayFitHeight(
            if (animatedAllDayHeight == null) maxHeight + allDayHeight else maxHeight,
        )
        val hourHeight = zoom.scale.hourHeight(fitHeight)
        val totalHeight = hourHeight * 24
        // The pinch sits above the scroll viewport: it has to outrank the
        // vertical scroll, and it does that by watching the initial pass, which
        // only reaches it if it is the viewport's ancestor.
        val pinch = rememberTimelinePinchZoom(scrollState, fitHeight, hourHeight, zoom)
        // What a block's text is fitted to: the column it will end up in once a
        // view switch is past half way, so it reflows once rather than per frame.
        val textColumns by remember(geometry) {
            derivedStateOf { if (geometry.progress() >= 0.5f) geometry.to.count else geometry.from.count }
        }
        val columnWidth = (maxWidth - TIMELINE_CONTENT_END_INSET + COLUMN_GAP) / textColumns - COLUMN_GAP
        // What the scroll shows, known before it is laid out: a view switch
        // picks what travels on its first frame.
        val opening = LocalOpeningTimeline.current
        val viewportPx = with(density) { maxHeight.toPx() }
        val visibleViewport: () -> Float = remember(animatedAllDayHeight, viewportPx, density) {
            { viewportPx - (animatedAllDayHeight?.let { with(density) { it().toPx() } } ?: 0f) }
        }
        val contentPx = with(density) { totalHeight.toPx() }
        val visibleTop: () -> Float = remember(opening, scrollState, visibleViewport, contentPx) {
            { opening?.top(visibleViewport(), contentPx) ?: scrollState.value.toFloat() }
        }
        // Scrolls on the same state as the gutter and the other pages, so they
        // all stay aligned. A static, rounded-clipped window — the content
        // scrolls inside it, so the soft corners are permanent at any scroll
        // position (not just at the day's start/end).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pinch)
                .padding(end = TIMELINE_CONTENT_END_INSET)
                .clip(RoundedCornerShape(TIMELINE_CORNER))
                .verticalScroll(scrollState)
                .onGloballyPositioned { if (active) dragController.geometry.viewport = it }
                // Past midnight, outside the columns: nothing there takes a tap or a drop.
                .padding(bottom = FabClearance),
        ) {
            TimelineColumns(
                geometry = geometry,
                gap = COLUMN_GAP,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    // The scrolling content itself, so its root position
                    // already folds in the scroll offset. Only the page on
                    // screen publishes it: the ones either side are laid out too.
                    .onGloballyPositioned { coords ->
                        if (!active) return@onGloballyPositioned
                        val gap = with(density) { COLUMN_GAP.toPx() }
                        dragController.geometry.let {
                            it.grid = coords
                            it.scroll = scrollState
                            it.hourPx = with(density) { hourHeight.toPx() }
                            it.blockInsetPx = blockInsetPx
                            it.columnGapPx = gap
                            it.columnWidthPx = (coords.size.width + gap) / geometry.to.count
                            it.days = geometry.dates
                            it.isRtl = isRtl
                        }
                    },
            ) { day ->
                DayColumnCard(
                    blocks = timedFor(day),
                    dark = dark,
                    date = day,
                    today = today,
                    hourHeight = hourHeight,
                    columnWidth = columnWidth,
                    visibleTop = visibleTop,
                    viewportHeight = visibleViewport,
                    actions = actions,
                    shape = columnShape(day == geometry.dates.first(), day == geometry.dates.last()),
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * The outer columns round their bottom corner at midnight: the room left below
 * for the buttons means the rounded window no longer does it there.
 */
private fun columnShape(first: Boolean, last: Boolean): Shape =
    if (!first && !last) RectangleShape
    else RoundedCornerShape(
        bottomStart = if (first) TIMELINE_CORNER else 0.dp,
        bottomEnd = if (last) TIMELINE_CORNER else 0.dp,
    )

private val TIMELINE_CORNER = 16.dp

@Composable
private fun DayColumnCard(
    blocks: List<TimedBlock>,
    dark: Boolean,
    date: LocalDate,
    today: LocalDate,
    hourHeight: Dp,
    columnWidth: Dp,
    visibleTop: () -> Float,
    viewportHeight: () -> Float,
    actions: TimelineActions,
    shape: Shape = RectangleShape,
    modifier: Modifier = Modifier,
) {
    val scrollState = actions.scrollState
    val dragController = actions.dragController
    val density = LocalDensity.current
    val hourPx = with(density) { hourHeight.toPx() }
    val morphing = LocalViewMorph.current != null
    val showHourGrid = LocalShowHourGrid.current
    val zone = LocalTimelineZone.current
    // A block drafted on an empty slot (#153), drawn over the part of it on this day.
    val draft by remember(dragController) {
        derivedStateOf { dragController.selection?.event?.takeIf { it.isDraft } }
    }
    val draftPiece = remember(draft, date, zone) { draft?.let { draftBlock(it, date, zone) } }
    val shown = remember(blocks, draftPiece) { if (draftPiece == null) blocks else blocks + draftPiece }
    // Tells a settled drop when this column has caught up with it.
    LaunchedEffect(shown, dragController.settling) {
        dragController.noteGrid(date, shown)
    }
    // The tap detector outlives the composition that installed it — a pinch or a
    // Settings change moves the hour height without restarting it — so it reads
    // the height through a state handle instead of capturing it, or taps land on
    // the scale the column had before the zoom (#148). The actions read their
    // callbacks' latest values themselves.
    val currentHourPx = rememberUpdatedState(hourPx)
    val edges = rememberOutOfViewEdges(blocks, scrollState, hourPx)
    val currentEdges = rememberUpdatedState(edges)
    val draftSurface = rememberDraftSurface(date, edges, hourPx, dragController, actions.onDrop)
    Card(
        // Plain rectangular columns — the soft corners come from the outer
        // rounded scroll viewport, apart from the outer ones' bottoms ([columnShape]).
        shape = shape,
        colors = CardDefaults.cardColors(
            // With the grid on, the container colour moves onto the hour cells
            // and the column behind them recedes to surface, so the gap between
            // two cells reads as the same negative space that separates the
            // week's columns.
            containerColor = if (showHourGrid) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
        LaneColumn(
            modifier = Modifier
                .fillMaxSize()
                // The hour cells sit over the column background but under the
                // event blocks (drawBehind paints before the children).
                .hourGridCells(showHourGrid, hourPx, MaterialTheme.colorScheme.surfaceContainer)
                // Tap an empty slot to create an event there; taps on event
                // blocks are consumed by their own handler first. Snaps to hour.
                .pointerInput(date) {
                    detectTapGestures { offset ->
                        if (dragController.selection != null) {
                            dragController.deselect()
                            return@detectTapGestures
                        }
                        if (currentEdges.value.tap(offset.y)) return@detectTapGestures
                        actions.onCreateAt(
                            date,
                            tappedMinuteOfDay(offset.y, currentHourPx.value),
                        )
                    }
                }
                // Inside the tap detector, which consumes every down, so only a
                // block or handle on top can claim the press first.
                .then(draftSurface),
        ) {
            // Keyed by event, so a block that changes time or lane is the *same*
            // composable afterwards and tweens there. The ordinal disambiguates
            // a column holding two occurrences of one series.
            val ordinals = mutableMapOf<Long, Int>()
            shown.forEach { block ->
                val ordinal = ordinals.merge(block.event.eventId, 1, Int::plus)!! - 1
                key(block.event.eventId, ordinal) {
                    // Where this block is cut at midnight, which decides both
                    // its shape and the half-gap it gives up to the hour cells.
                    val cuts = remember(block, date, zone) {
                        timedBlockCuts(
                            block.continuesBefore(date, zone),
                            block.continuesAfter,
                        )
                    }
                    val span = remember(block) { block.renderedSpan() }
                    val top = hourHeight * (span.topMin / 60f)
                    val height = hourHeight * ((span.bottomMin - span.topMin) / 60f)
                    val place = animatedBlockPlacement(
                        xFraction = block.lane.toFloat() / block.laneCount,
                        widthFraction = 1f / block.laneCount,
                        y = top,
                        height = height,
                        landing = dragController.ghostsDerived(block),
                    )
                    val topInset = hourCellBlockInset(showHourGrid, cuts.top)
                    val bottomInset = hourCellBlockInset(showHourGrid, cuts.bottom)
                    val blockHeight = (place.height - topInset - bottomInset)
                        .coerceAtLeast(0.dp)
                    // Only a block wholly on screen travels in a view switch: the
                    // rest would cross the header on their way. They fade with
                    // their screen, and the other view leaves them be too.
                    val travels = morphing && rememberInViewport(
                        visibleTop = visibleTop,
                        viewportHeight = viewportHeight,
                        top = with(density) { (top + topInset).toPx() },
                        bottom = with(density) { (top + height - bottomInset).toPx() },
                    )
                    val morphKey = ViewMorphKey.event(block.event, date)
                    StayPut(morphKey, stays = morphing && !travels)
                    EventBlock(
                        block = block,
                        dark = dark,
                        height = blockHeight,
                        width = columnWidth / block.laneCount,
                        date = date,
                        cuts = cuts,
                        topInset = topInset,
                        dragController = dragController,
                        onClick = { actions.onEventClick(block.event) },
                        onDrop = actions.onDrop,
                        onResize = actions.onResize,
                        modifier = Modifier
                            .laneSlot(place)
                            .offset(y = place.y + topInset)
                            .then(if (travels) Modifier.morphEvent(morphKey) else Modifier)
                            .height(blockHeight)
                            .padding(horizontal = BLOCK_OUTER_INSET),
                    )
                }
            }
            // Current-time line, on top of the events, only on today's column.
            if (date == today) {
                NowLine(date = date, hourHeight = hourHeight)
            }
        }
            OutOfViewEdgeBars(
                edges = edges,
                scrollState = scrollState,
                dark = dark,
            )
        }
    }
}

/** Whether [top]..[bottom], in pixels down the scrolled content, lies wholly in its viewport. */
@Composable
private fun rememberInViewport(visibleTop: () -> Float, viewportHeight: () -> Float, top: Float, bottom: Float): Boolean {
    val inView by remember(visibleTop, viewportHeight, top, bottom) {
        derivedStateOf { visibleTop().let { top >= it && bottom <= it + viewportHeight() } }
    }
    return inView
}

@Composable
private fun EventBlock(
    block: TimedBlock,
    dark: Boolean,
    height: Dp,
    width: Dp,
    date: LocalDate,
    cuts: ChipCuts,
    topInset: Dp,
    dragController: TimelineDragController,
    onClick: () -> Unit,
    onDrop: (TimelineDrop) -> Unit,
    onResize: (TimelineResizeDrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = block.event.title.ifBlank { stringResource(R.string.event_untitled) }
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val zone = LocalTimelineZone.current
    val labelEndMin = remember(block, date, zone) { block.labelEndMin(date, zone) }
    val timeLabel = "${minToHm(block.startMin, use24Hour, locale)}–" +
        minToHm(labelEndMin, use24Hour, locale)
    val density = LocalDensity.current
    val metrics = rememberBlockTextMetrics(height)
    // Only full-width (non-overlapping) blocks that are tall enough show the
    // time. On narrow overlapping columns we drop it so the title can wrap to
    // fill the whole block, mirroring Google Calendar — and a block that cannot
    // afford both lines spends its space on the title. Height decides that on
    // its own: a duration threshold would keep hiding the time on a half-hour
    // block the user has pinched open to three times the room it needs.
    val showTime = block.laneCount == 1 &&
        metrics.available >= metrics.titleLine + metrics.timeLine
    val textWidth = width - (BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING) * 2
    val shownTime = rememberBlockTimeRange(
        minToHm(block.startMin, use24Hour, locale),
        minToHm(labelEndMin, use24Hour, locale),
        textWidth,
    )
    // A short block drops the title rather than serving a horizontally sliced
    // one: half a letter reads as a rendering fault, while a bare colour chip
    // reads as what it is — an event too brief to label. Tap still opens it, and
    // the semantics description carries the full title either way.
    val showTitle = metrics.fitsTitle
    // The title is served first, out of everything the block has left once the
    // time is down to one line — but only takes the lines it will actually use,
    // and only wraps at all once a line is wide enough to hold more than a
    // syllable. Below that the extra lines just stack fragments of the word.
    val contentHeight = metrics.available - if (showTime) metrics.timeLine else 0.dp
    val titleBudget = metrics.titleBudget(contentHeight).coerceAtLeast(1)
    val paint = eventPaint(block.event, dark)
    // Every line the height affords, however narrow the lane: two events side by
    // side leave columns well under a word wide, and cutting the title to one
    // line there lost it outright where the block had the room to wrap it.
    val titleMaxLines = blockTextLines(
        text = title,
        // At the weight BlockTitle will set it in, or an invited block — drawn a
        // weight lighter — is budgeted a line it never fills (#230).
        style = MaterialTheme.typography.labelMedium.withTitleWeight(paint),
        textWidth = textWidth,
        max = titleBudget,
    )
    val spare = metrics.available - metrics.titleHeight(titleMaxLines) -
        if (showTime) metrics.timeLine else 0.dp
    val timeMaxLines = if (showTime) blockTimeLines(shownTime, textWidth, spare) else 1
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && block.event.isPastOn(date, dimCutoff)
    val moveAction = eventMoveAction(block.event)
    val draft = block.event.isDraft
    val draggable = draft || eventDragAllowed(block.event)
    val selectable = eventSelectAllowed()
    val selected = dragController.isSelected(block)
    val haptics = LocalHapticFeedback.current
    // The drop takes this offset back off, so a tail clipped at midnight lands
    // where the event's own start belongs (#253).
    val clipOffset = remember(block, date, zone) { block.clipOffsetMinutes(date, zone) }
    val shape = remember(cuts) { timedBlockShape(cuts.top, cuts.bottom) }
    val topInsetPx = with(density) { topInset.toPx() }
    // Long press (#141): move to drag, let go to select; selected blocks drag at once.
    val dragModifier = rememberEventDragSource(
        enabled = selectable,
        key = block.event.instanceId,
        immediate = selected && draggable,
        onPickUp = { pointer, blockRoot, _ ->
            if (draggable) {
                dragController.begin(
                    block = block,
                    clipOffsetMin = clipOffset,
                    zone = zone,
                    titleLines = if (showTitle) titleMaxLines else 0,
                    pointerInRoot = pointer,
                    // Measured off the block's placement rather than off where the
                    // hour grid seats it: the copy is drawn from that placement and
                    // re-seated the same half-gap down (#267).
                    blockInRoot = blockRoot.copy(y = blockRoot.y - topInsetPx),
                )
                PickUp.Drag
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                dragController.select(block.event)
                PickUp.Hold
            }
        },
        onMove = dragController::move,
        onDrop = { dragController.finish()?.let(onDrop) },
        onCancel = dragController::cancel,
        onRelease = { dragController.select(block.event, fromLift = draggable) },
    )
    val lifted = draggable && dragController.ghostsDerived(block)
    // Released in place, the block takes over the copy's lift without a dip.
    val takesOverLift = selected && dragController.selection?.fromLift == true
    val ghostFade = ghostAlpha(lifted)
    val ghost = when {
        takesOverLift && !lifted -> 1f
        // A draft leaves no ghost behind, and the copy it lands under is identical.
        draft -> if (lifted) 0f else 1f
        else -> ghostFade
    }
    val raise = selectionLift(selected, snap = takesOverLift)
    // Tells the action bar to fold when it would cover the bottom handle.
    val revealExtraPx = with(density) { (ResizeHandleTarget / 2).toPx() }
    val barClearancePx = with(density) { SelectionBarClearance.toPx() }
    val tracksBar = selected && !cuts.bottom
    // Not while its own write is pending: it still shows the old times.
    LaunchedEffect(selected, block.event, lifted) {
        if (selected && !lifted) dragController.adoptSelection(block)
    }
    // Kept composed while hidden: removing them would cancel their own resize.
    DisposableEffect(selected, block.event) {
        if (selected) dragController.noteSelectedBlock(block.event, true)
        onDispose { if (selected) dragController.noteSelectedBlock(block.event, false) }
    }
    val showHandles = selected && draggable
    val handlesShown = !lifted && !dragController.isDragging
    Box(
        modifier = (if (dimmed && !selected) modifier.alpha(EventDimAlpha) else modifier)
            .zIndex(if (selected) 1f else 0f)
            // The source stays put as a ghost while its floating copy travels.
            .then(if (ghost < 1f) Modifier.alpha(ghost) else Modifier)
            .then(if (raise > 0f) Modifier.selectionLift(raise, shape) else Modifier)
            .then(
                if (tracksBar) {
                    Modifier.onGloballyPositioned {
                        dragController.noteSelectedBottom(it, revealExtraPx, barClearancePx)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .eventSurface(paint, shape, cuts)
                .clickable(onClick = onClick)
                // After clickable, so it is the inner node and wins the main pass;
                // the tap still works, since a drag consumes the up.
                .then(dragModifier)
                .padding(horizontal = BLOCK_TEXT_PADDING, vertical = metrics.inset)
                .semantics {
                    contentDescription = "$title, $timeLabel"
                    if (moveAction != null) customActions = listOf(moveAction)
                },
        ) {
            Column {
                if (showTitle) {
                    BlockTitle(
                        title = title,
                        maxLines = titleMaxLines,
                        color = paint.titleInk,
                        textDecoration = paint.decoration,
                        fontWeight = paint.titleWeight,
                    )
                }
                if (showTime) {
                    BlockTimeLabel(
                        label = shownTime,
                        color = paint.secondaryInk,
                        maxLines = timeMaxLines,
                    )
                }
            }
        }
        if (showHandles) {
            val accent = eventAccent(block.event.color, dark, LocalSoftenColors.current)
            val handleLines = if (showTitle) titleMaxLines else 0
            val begin = { edge: ResizeEdge ->
                { pointer: Offset ->
                    dragController.beginResize(block, edge, zone, handleLines, pointer)
                }
            }
            val end = { dragController.finishResize(zone)?.let(onResize) }
            // No bounce: an overshoot reads as the block's ends jumping.
            val handleIn by animateFloatAsState(
                targetValue = if (handlesShown) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "handles",
            )
            val handleAlpha = Modifier.graphicsLayer {
                alpha = handleIn.coerceIn(0f, 1f)
                scaleX = 0.5f + 0.5f * handleIn
                scaleY = 0.5f + 0.5f * handleIn
            }
            if (!cuts.top) {
                ResizeHandle(
                    color = accent,
                    enabled = handlesShown,
                    onStart = begin(ResizeEdge.Top),
                    onMove = dragController::move,
                    onEnd = { end() },
                    onCancel = dragController::cancel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = HANDLE_INSET - ResizeHandleTarget / 2, y = -ResizeHandleTarget / 2)
                        .then(handleAlpha),
                )
            }
            if (!cuts.bottom) {
                ResizeHandle(
                    color = accent,
                    enabled = handlesShown,
                    onStart = begin(ResizeEdge.Bottom),
                    onMove = dragController::move,
                    onEnd = { end() },
                    onCancel = dragController::cancel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = ResizeHandleTarget / 2 - HANDLE_INSET, y = ResizeHandleTarget / 2)
                        .then(handleAlpha),
                )
            }
        }
    }
}

/**
 * A long press on an empty stretch of [date]'s column (#153): drafts a block of
 * the default length from the slot under the finger and lifts it, as a long
 * press on an event does. Presses on a block or handle are left to them.
 */
@Composable
private fun rememberDraftSurface(
    date: LocalDate,
    edges: OutOfViewEdges,
    hourPx: Float,
    dragController: TimelineDragController,
    onDrop: (TimelineDrop) -> Unit,
): Modifier {
    val currentDrop by rememberUpdatedState(onDrop)
    val title = stringResource(R.string.event_edit_new_title)
    val color = MaterialTheme.colorScheme.primary.toArgb()
    val zone = LocalTimelineZone.current
    val currentEdges by rememberUpdatedState(edges)
    // The draft this gesture lifted, selected again on release after the cancel.
    val lifted = remember { arrayOfNulls<EventInstance>(1) }
    val currentHourPx by rememberUpdatedState(hourPx)
    return rememberDragSurface(
        enabled = eventSelectAllowed(),
        key = date,
        skipClaimedDown = true,
        // A press on a block or handle was consumed by it and never gets here.
        onPickUp = { local, pointer, node, _ ->
            val hour = currentHourPx
            if (hour <= 0f || currentEdges.covers(local.y)) return@rememberDragSurface false
            val startMin = draftStartMin(local.y / hour * 60f)
            val event = draftEvent(
                title = title,
                color = color,
                start = wallClockInstant(date, startMin, zone),
                minutes = dragController.draftMinutes,
            )
            val block = draftBlock(event, date, zone) ?: return@rememberDragSurface false
            lifted[0] = event
            dragController.beginDraft(
                block = block,
                zone = zone,
                pointerInRoot = pointer,
                blockInRoot = node + Offset(0f, block.renderedSpan().topMin / 60f * hour),
            )
            true
        },
        onMove = dragController::move,
        onDrop = { dragController.finish()?.let(currentDrop) },
        // An abandoned pickup leaves no draft behind; a release selects it again below.
        onCancel = {
            dragController.cancel()
            if (dragController.selection?.event?.isDraft == true) dragController.deselect()
        },
        onRelease = { lifted[0]?.let { dragController.select(it, fromLift = true) } },
    )
}

private const val SELECTION_ORPHAN_GRACE_MILLIS = 500L

/** How far in from the block's side a resize handle's centre sits. */
private val HANDLE_INSET = 16.dp

private val SKELETON_HEADER_HEIGHT = 36.dp
private val SKELETON_HEADER_PADDING = 8.dp

@Composable
private fun WeekLoading(dayCount: Int) {
    val scale = LocalTimelineZoom.current.scale
    val scrollState = rememberScrollState()
    val headerHeight = weekDayHeaderHeight()
    Column(modifier = Modifier.fillMaxSize()) {
        // Header skeleton
        Row(modifier = Modifier.fillMaxWidth().padding(SKELETON_HEADER_PADDING)) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            repeat(dayCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(SKELETON_HEADER_HEIGHT)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Same scale resolution as the loaded timeline, so the skeleton's
            // columns don't resize the moment the real week arrives.
            val fitHeight = dayFitHeight(
                maxHeight + SKELETON_HEADER_HEIGHT + SKELETON_HEADER_PADDING * 2 -
                    headerHeight - TIMELINE_TOP_GAP,
            )
            val hourHeight = scale.hourHeight(fitHeight)
            val totalHeight = hourHeight * 24
            // The skeleton wears the loaded columns' own ground, so the arrival
            // of the real week doesn't flash solid blocks into a gapped grid.
            val showHourGrid = LocalShowHourGrid.current
            val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
            val columnGround = if (showHourGrid) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceContainer
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = TIMELINE_CONTENT_END_INSET)
                    .verticalScroll(scrollState)
                    .padding(bottom = FabClearance),
            ) {
                Spacer(Modifier.width(GUTTER_WIDTH))
                repeat(dayCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight)
                            .padding(horizontal = 2.dp)
                            .background(columnGround)
                            .hourGridCells(
                                showHourGrid,
                                hourPx,
                                MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                }
            }
        }
    }
}

private fun minToHm(min: Int, is24Hour: Boolean, locale: java.util.Locale): String =
    formatMinuteOfDay(min, is24Hour, locale)

/**
 * Every timeline's title, the day view's included: just the month [weekStart] falls in.
 *
 * The day numbers are already in the column headers directly below, so spelling
 * out "13.–19. Jul" restates them in the widest string in the bar. Naming the
 * month of [weekStart] means a week straddling a boundary keeps the outgoing
 * month until it is fully gone — a week is seven contiguous days, so the earlier
 * month has a day in it exactly while [weekStart] is still inside it. That also
 * makes the *month* depend on nothing but [weekStart], so it cannot drift with
 * the direction you paged in from.
 *
 * The *year* is decided from the whole week, not just its start: a week running
 * Dec 28 – Jan 3 has four of its seven visible columns in the new year, so
 * "December" with no year would be actively misleading while the reader is
 * looking straight at January dates.
 */
private fun formatTimelineTitle(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    locale: Locale,
    currentYear: Int,
    abbreviated: Boolean = false,
): String {
    return formatCalendarTitle(
        date = weekStart.yearMonth.firstDay.toJavaLocalDate(),
        locale = locale,
        currentYear = currentYear,
        skeleton = if (abbreviated) "LLL" else "LLLL",
        forceYear = weekEnd.year != weekStart.year,
    )
}
