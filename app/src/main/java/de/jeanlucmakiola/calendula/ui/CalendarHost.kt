package de.jeanlucmakiola.calendula.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.ui.common.rememberHeld
import de.jeanlucmakiola.calendula.ui.common.screen
import de.jeanlucmakiola.calendula.ui.common.ViewScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.data.time.currentDate
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaScreen
import de.jeanlucmakiola.calendula.ui.calendars.BackupScreen
import de.jeanlucmakiola.calendula.ui.calendars.CalendarsScreen
import de.jeanlucmakiola.calendula.ui.common.BelowTopBarClip
import de.jeanlucmakiola.calendula.ui.common.StayingPut
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFab
import de.jeanlucmakiola.calendula.ui.common.CalendarTopBar
import de.jeanlucmakiola.calendula.ui.common.ChromeHost
import de.jeanlucmakiola.calendula.ui.common.ChromeSlot
import de.jeanlucmakiola.calendula.ui.common.LocalChromeSlot
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.identity.fadeThrough
import de.jeanlucmakiola.floret.identity.predictiveBackExit
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.EventActionsViewModel
import de.jeanlucmakiola.calendula.ui.common.EventMoveHost
import de.jeanlucmakiola.calendula.ui.common.EventSelectionHost
import de.jeanlucmakiola.calendula.ui.common.eventActionFailureMessage
import de.jeanlucmakiola.calendula.ui.common.rememberFabSelection
import de.jeanlucmakiola.calendula.ui.common.canWriteCalendar
import de.jeanlucmakiola.calendula.ui.common.rememberWriteAction
import de.jeanlucmakiola.calendula.ui.common.EventMoveScope
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.LocalManageCalendars
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineScroll
import de.jeanlucmakiola.calendula.ui.common.LocalViewFocus
import de.jeanlucmakiola.calendula.ui.common.ViewFocus
import de.jeanlucmakiola.calendula.ui.common.ViewFocusSaver
import de.jeanlucmakiola.calendula.ui.common.LocalViewMorph
import de.jeanlucmakiola.calendula.ui.common.RescheduleViewModel
import de.jeanlucmakiola.calendula.ui.common.TimelineScrollMemory
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.ViewMorphScope
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.rememberViewMorphBoundsTransform
import de.jeanlucmakiola.calendula.ui.common.drillToDay
import de.jeanlucmakiola.calendula.ui.common.drillToWeek
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.selectView
import de.jeanlucmakiola.calendula.ui.common.viewBaseStack
import de.jeanlucmakiola.calendula.ui.detail.EventDetailScreen
import de.jeanlucmakiola.calendula.ui.edit.EventEditViewModel
import de.jeanlucmakiola.calendula.ui.edit.EventEditScreen
import de.jeanlucmakiola.calendula.ui.edit.ImportSource
import de.jeanlucmakiola.calendula.ui.imports.ImportScreen
import de.jeanlucmakiola.calendula.ui.month.MonthScreen
import de.jeanlucmakiola.calendula.ui.search.SearchScreen
import de.jeanlucmakiola.calendula.ui.settings.SettingsScreen
import de.jeanlucmakiola.calendula.ui.week.TimelineScreen
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

/**
 * Holds the top-level view back stack (spec M1) and swaps between the calendar
 * screens. Each screen owns its own ViewModel; they all open on the one focused
 * date held here ([ViewFocus]), and the view-switcher pill in their top bars
 * writes back here via [onSelectView].
 *
 * The stack's bottom is the user's [CalendarHostViewModel.defaultView] home view.
 * A lateral switch (pill / drawer) builds a visit history so back retraces it
 * (see [selectView]); a widget launch resets the stack to its own view; a date
 * tap drills the day view on top. Pressing back pops one level, and the
 * base-level [BackHandler] hands off to the system to exit once only the home
 * view remains. So back from a widget-opened screen returns to that widget's
 * view, then home; and back through pill switches walks the views in reverse.
 *
 * [requests] carries what notifications, widgets and other apps asked to
 * open. A tapped reminder's occurrence opens the detail overlay exactly like an
 * event tap; each request is cleared once acted on so a later recomposition
 * can't re-open it. (A widget event tap instead arrives as
 * [WidgetNavRequest.OpenEvent], which also roots the back stack in the widget's
 * view.)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CalendarHost(
    requests: ExternalRequests,
    modifier: Modifier = Modifier,
    viewModel: CalendarHostViewModel = hiltViewModel(),
) {
    // Wait for the persisted default view before seeding the stack, so the app
    // opens straight on the user's choice instead of flashing a placeholder and
    // correcting it. Brief blank first frame, matching the onboarding gate above.
    val defaultView = viewModel.defaultView.collectAsStateWithLifecycle().value ?: return

    // View customisation (#24): the quick-switch cycle and drawer order. Both have
    // sensible non-empty initial values, so they're ready before the first frame.
    val quickSwitchViews = viewModel.quickSwitchViews.collectAsStateWithLifecycle().value
    val drawerViewOrder = viewModel.drawerViewOrder.collectAsStateWithLifecycle().value
    // Whether the jump-to-today control sits in each view's top bar or the FAB (#60).
    val todayInToolbar = viewModel.todayButtonInToolbar.collectAsStateWithLifecycle().value

    var viewStack by rememberSaveable(stateSaver = viewStackSaver) {
        mutableStateOf(listOf(defaultView))
    }
    var navigationVersion by remember { mutableIntStateOf(0) }
    // True after backing out of a day opened from the view now shown, so the
    // agenda returns to where it was instead of today (#367).
    var returnedFromDay by remember { mutableStateOf(false) }
    val view = viewStack.last()
    // The date every view opens on and carries along (#184); tapping a day
    // anywhere focuses it and opens the day view there.
    val focus = rememberSaveable(saver = ViewFocusSaver) { ViewFocus(currentDate()) }
    // A focus still on yesterday follows the date over, so the next view opened
    // doesn't land on the old day (#373); one moved elsewhere stays put.
    val today = viewModel.today.collectAsStateWithLifecycle().value
    var focusToday by rememberSaveable { mutableStateOf(today.toString()) }
    LaunchedEffect(today) {
        val old = LocalDate.parse(focusToday)
        if (old != today && focus.date == old) focus.date = today
        focusToday = today.toString()
    }

    val switchScope = rememberCoroutineScope()
    val switchTo: ((List<CalendarView>) -> List<CalendarView>) -> Unit = { move ->
        navigationVersion++
        returnedFromDay = false
        viewStack = move(viewStack)
    }
    val onSelectView: (CalendarView) -> Unit = { target -> switchTo { it.selectView(target) } }
    val onOpenDay: (LocalDate) -> Unit = { date ->
        focus.navigateTo(date)
        switchTo { it.drillToDay() }
    }
    val onOpenWeek: (LocalDate) -> Unit = { date ->
        focus.navigateTo(date)
        switchTo { it.drillToWeek() }
    }

    // The event-detail screen (S4) is a full-screen destination hoisted here so
    // it overlays whichever calendar view is active. We forward the tapped
    // occurrence's own times (eventId + begin + end, packed as a saveable
    // long[]) so recurring events show the correct date, not the series start.
    // Like every overlay below, it draws its last key through the slide-out
    // ([rememberHeld]) rather than a placeholder.
    var detailKey by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val onOpenDetail: (EventInstance) -> Unit = { event -> detailKey = event.occurrenceKey() }

    // A tapped reminder notification asks for a specific occurrence.
    val requestedDetailKey = requests.detailKey
    LaunchedEffect(requestedDetailKey) {
        if (requestedDetailKey != null) {
            detailKey = requestedDetailKey
            requests.detailKey = null
        }
    }

    // Settings (M4) is hoisted here so it overlays whichever calendar view is
    // active and survives view switches. (The calendar filter now lives inline
    // in the navigation drawer, so no overlay state is needed for it.)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val onOpenSettings = { showSettings = true }

    // Full-text search — its own overlay, opened from each calendar screen's
    // top bar. Sits below the detail/edit overlays so tapping a result reveals
    // the detail on top and backing out returns to the results.
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val onOpenSearch = { showSearch = true }

    // Calendar manager (reached from Settings) — its own overlay so it slides
    // over Settings and survives view switches.
    var showCalendars by rememberSaveable { mutableStateOf(false) }

    // Backup & restore (#69) — hoisted like the manager, being driven by the
    // calendar list rather than by preferences. Reached from both.
    var showBackup by rememberSaveable { mutableStateOf(false) }

    // Event form (v1.2 create), opened on a date, a tapped slot or a drafted block.
    var createRequest by rememberSaveable(stateSaver = createRequestSaver) {
        mutableStateOf<CreateRequest?>(null)
    }
    fun openCreate(dateIso: String, startMinutes: Int? = null, draftMillis: LongArray? = null) {
        createRequest = CreateRequest(dateIso, startMinutes, draftMillis)
    }
    val onCreateEvent: (LocalDate, Int?) -> Unit = { date, startMinutes -> openCreate(date.toString(), startMinutes) }
    val onCreateDraft: (Instant, Instant) -> Unit = { start, end ->
        val date = start.toLocalDateTime(TimeZone.currentSystemDefault()).date
        openCreate(date.toString(), draftMillis = longArrayOf(start.toEpochMilliseconds(), end.toEpochMilliseconds()))
    }

    // Edit form (v1.3) — reuses the detail screen's occurrence key; for
    // recurring events the form itself asks for the write scope at save
    // time. A saved edit closes the detail screen too: the occurrence the
    // user tapped may not exist anymore (time moved, recurrence changed), so
    // falling back to the auto-refreshing calendar is the only honest
    // destination.
    var editKey by rememberSaveable { mutableStateOf<LongArray?>(null) }

    // An opened/received .ics file. [ImportScreen] parses it and either opens
    // the prefilled create form (one event → [importForm]) or its own bulk
    // picker (many). A plain conditional overlay (no slide) — it's transient.
    var importUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val importViewModel: EventEditViewModel = hiltViewModel(key = "import")
    // Saved as "open"; a recreation takes the form back from the ViewModel, which
    // still holds the user's edits.
    var importForm by rememberSaveable(
        stateSaver = Saver<EventForm?, Boolean>(
            save = { it != null },
            restore = { open -> if (open) importViewModel.currentForm() else null },
        ),
    ) { mutableStateOf<EventForm?>(null) }
    // Which channel filled [importForm]: an .ics file (prompt to apply the default
    // reminder) or an ACTION_INSERT intent (apply it automatically) — #49.
    var importFormSource by rememberSaveable { mutableStateOf(ImportSource.File) }
    // Bumped per open, so even an equal prefill starts a fresh form.
    var importFormId by rememberSaveable { mutableIntStateOf(0) }
    // Whether [importForm] came from an external create request (#30, #306);
    // closing it then hands control back to whoever asked.
    var importFromRequest by rememberSaveable { mutableStateOf(false) }
    // A second external request that arrived while a prefilled form was open:
    // it waits behind that form's unsaved-changes prompt, then opens.
    var pendingInsert by remember { mutableStateOf<Pair<EventForm, ImportSource>?>(null) }
    val createViewModel: EventEditViewModel = hiltViewModel(key = "create")
    val editViewModel: EventEditViewModel = hiltViewModel(key = "edit")
    fun openImportForm(form: EventForm, source: ImportSource, fromRequest: Boolean = false) {
        importFormSource = source
        importForm = form
        importFormId++
        importFromRequest = fromRequest
    }
    fun closeImportForm() {
        val next = pendingInsert
        pendingInsert = null
        if (next != null) {
            openImportForm(next.first, next.second, fromRequest = true)
        } else {
            importForm = null
            if (importFromRequest) {
                importFromRequest = false
                requests.onExternalFormClosed()
            }
        }
    }
    // A restore (in-app "Restore from .ics" button) always runs the full import
    // flow — picker + summary — even for a single-event file, because the intent
    // is "restore a backup", not "add this one event". An externally opened .ics
    // keeps routing a single event straight into the prefilled create form.
    var importForceMany by rememberSaveable { mutableStateOf(false) }
    // Where a restore came from, so closing the import puts it back: the import
    // overlays are declared under Backup & restore and the manager, so starting
    // one has to close them — without this, finishing a restore drops you on the
    // calendar and the next file means walking in through Settings again.
    var backupAfterImport by rememberSaveable { mutableStateOf(false) }
    var calendarsAfterImport by rememberSaveable { mutableStateOf(false) }
    // One import run. The import VM lives in the Activity's store, so this is
    // what tells it a *re-import of the same file* is new work and not the run
    // it already finished; a rotation keeps the number and keeps the result.
    var importSession by rememberSaveable { mutableStateOf(0) }
    // A recreation re-reads the launch intent; when the import it carries came
    // back with the saved state, that replay is the same work, not a new one.
    fun isReplayOfRestoredImport(): Boolean {
        val replay = requests.replayed && (importUri != null || importForm != null)
        requests.replayed = false
        return replay
    }
    val requestedImportUri = requests.importUri
    LaunchedEffect(requestedImportUri) {
        if (requestedImportUri != null) {
            if (isReplayOfRestoredImport()) {
                requests.importUri = null
                return@LaunchedEffect
            }
            importUri = requestedImportUri
            importForceMany = false
            importSession++
            requests.importUri = null
        }
    }
    // An external ACTION_INSERT launch (another app/widget creating an event,
    // issue #30) arrives already prefilled — open it in the same create form the
    // single-event .ics path uses. [importForm] is the topmost overlay, so it
    // reveals on top of whatever was open without extra dismissal.
    val requestedInsert = requests.insert
    LaunchedEffect(requestedInsert) {
        if (requestedInsert != null) {
            if (isReplayOfRestoredImport()) {
                requests.insert = null
                return@LaunchedEffect
            }
            if (importForm == null) {
                openImportForm(requestedInsert.form, requestedInsert.source, fromRequest = true)
            } else {
                pendingInsert = requestedInsert.form to requestedInsert.source
            }
            requests.insert = null
        }
    }

    // Close every overlay that can sit over the calendar, so an externally
    // requested destination (a widget/shortcut/QS-tile launch) is revealed on
    // top instead of underneath whatever the user had open. The create form,
    // declared under the edit form, can stay for an edit to open over it.
    fun dismissCoveringOverlays(keepCreate: Boolean = false) {
        showSettings = false
        showSearch = false
        showCalendars = false
        showBackup = false
        detailKey = null
        editKey = null
        if (!keepCreate) createRequest = null
        importUri = null
        importForm = null
        if (importFromRequest) {
            importFromRequest = false
            requests.onExternalFormClosed()
        }
        pendingInsert = null
        importViewModel.reset()
    }

    // An external "edit this event" (ACTION_EDIT, e.g. an assistant/task app or
    // widget) opens the occurrence straight in the edit form. Drop any covering
    // overlay first — the edit overlay sits below Settings/import in the Box, so
    // without this it would open hidden underneath them. A saved edit just
    // returns to the calendar.
    val requestedEditKey = requests.editKey
    LaunchedEffect(requestedEditKey) {
        if (requestedEditKey != null) {
            // An open edit form stays on screen, so it starts over here rather
            // than once it has slid away.
            if (editKey != null) editViewModel.reset()
            dismissCoveringOverlays(keepCreate = true)
            editKey = requestedEditKey
            requests.editKey = null
        }
    }

    // A home-screen widget launch asks to open a date (→ day view), open an
    // event's detail, or start a create. Handled once and cleared, mirroring
    // the detail request. Date/event opens root the stack in the widget's own
    // view so backing out returns there (then home), not to the default.
    val widgetNavRequest = requests.nav
    LaunchedEffect(widgetNavRequest) {
        if (widgetNavRequest != null) {
            navigationVersion++
            returnedFromDay = false
        }
        when (val req = widgetNavRequest) {
            is WidgetNavRequest.OpenDate -> {
                // Drill the day view in over the widget's view: drop any overlay
                // that would cover it, so the open doesn't land under Settings/form.
                dismissCoveringOverlays()
                focus.navigateTo(LocalDate.parse(req.dateIso))
                // No widget source (an external date tap) roots over the default
                // home view, so backing out of the day returns home then exits.
                viewStack = viewBaseStack(defaultView, req.source ?: defaultView).drillToDay()
                requests.nav = null
            }
            is WidgetNavRequest.OpenEvent -> {
                // Root the stack in the widget's view, then open the occurrence
                // detail over it (same key shape as a tapped event / reminder).
                dismissCoveringOverlays()
                viewStack = viewBaseStack(defaultView, req.source)
                detailKey = longArrayOf(req.eventId, req.beginMillis, req.endMillis)
                requests.nav = null
            }
            is WidgetNavRequest.OpenView -> {
                // A widget header tap: land on a top-level view with no date
                // drill-in. Reveal it by dropping any covering overlay, then root
                // the stack on the target (null → the default home) over the
                // default home — so backing out returns to the default, then exits.
                dismissCoveringOverlays()
                focus.navigateTo(currentDate())
                viewStack = viewBaseStack(defaultView, req.view ?: defaultView)
                requests.nav = null
            }
            is WidgetNavRequest.Create -> {
                // External "new event" entries (QS tile / launcher shortcut /
                // widget) must land on top of whatever is open — the form overlay
                // sits below Settings/calendars in the Box, so without this it
                // would open hidden underneath them.
                if (createRequest != null) createViewModel.reset()
                dismissCoveringOverlays()
                openCreate(req.dateIso ?: currentDate().toString())
                requests.nav = null
            }
            null -> {}
        }
    }

    // Drag to reschedule (#68). Hosted here so one instance serves every view and
    // survives view switches; the calendar surfaces read it out of LocalEventMove.
    val reschedule: RescheduleViewModel = hiltViewModel()
    val movableCalendarIds by reschedule.movableCalendarIds.collectAsStateWithLifecycle()
    val eventActions: EventActionsViewModel = hiltViewModel()
    val moveOutcome by reschedule.outcome.collectAsStateWithLifecycle()
    val selectionFailure by eventActions.failure.collectAsStateWithLifecycle()
    val onEditEvent: (EventInstance) -> Unit = { event -> editKey = event.occurrenceKey() }
    // A tap opens the edit form instead of the detail screen when the user chose
    // so (#178) — only for events whose calendar takes writes. Both are read at
    // tap time, so a tap before they've loaded still routes right.
    val tapScope = rememberCoroutineScope()
    val tapContext = LocalContext.current
    // A refused write upgrade is asked once; later taps just open the details.
    var tapWriteRefused by rememberSaveable { mutableStateOf(false) }
    // The tap waiting on the WRITE_CALENDAR prompt, saved so a rotation keeps it.
    var tapAwaitingWrite by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val editAwaitingTap = rememberWriteAction(
        onDenied = {
            tapAwaitingWrite?.let { key ->
                tapWriteRefused = true
                detailKey = key
            }
            tapAwaitingWrite = null
        },
    ) {
        tapAwaitingWrite?.let { editKey = it }
        tapAwaitingWrite = null
    }
    val hebrewContext = androidx.compose.ui.platform.LocalContext.current
    val onEventClick: (EventInstance) -> Unit = { event ->
      if (de.jeanlucmakiola.calendula.hebrew.HebrewCal.isSynthetic(event)) {
        android.widget.Toast.makeText(hebrewContext, event.title, android.widget.Toast.LENGTH_SHORT).show()
      } else
        tapScope.launch {
            val toEditor = viewModel.tapOpensEditor() && eventActions.canModify(event.calendarId)
            when {
                !toEditor -> onOpenDetail(event)
                tapContext.canWriteCalendar() -> onEditEvent(event)
                tapWriteRefused -> onOpenDetail(event)
                else -> {
                    tapAwaitingWrite = event.occurrenceKey()
                    editAwaitingTap()
                }
            }
        }
    }
    // Off by preference (#173) switches off the gesture, not the scope: blocks
    // register no drag, while the edit form and its TalkBack action stay put.
    val dragToReschedule = viewModel.dragToReschedule.collectAsStateWithLifecycle().value
    val moveScope = remember(movableCalendarIds, reschedule, dragToReschedule) {
        EventMoveScope(
            movableCalendarIds = movableCalendarIds,
            dragEnabled = dragToReschedule,
            move = { reschedule.move(it) },
            drops = reschedule.drops,
            undoStarted = reschedule.undoStarted,
            edit = onEditEvent,
        )
    }

    // Base-level back: pop the view stack while no overlay covers it (each overlay
    // owns its own BackHandler and takes precedence). Disabled at the home view,
    // so back there falls through to the system and exits the app.
    val anyOverlayVisible = showSearch || detailKey != null || createRequest != null ||
        editKey != null || showSettings || showCalendars || showBackup ||
        importUri != null || importForm != null
    // The view switch runs on a seekable transition, so a back gesture can scrub
    // it (#185): the previous view morphs in under the finger, releasing finishes
    // the pop and cancelling springs back. Reduced motion keeps a plain back.
    val viewTransition = remember { SeekableTransitionState(view) }
    LaunchedEffect(view) { viewTransition.animateTo(view) }
    val reduceMotion = rememberReduceMotion()
    val viewBackEnabled = !anyOverlayVisible && viewStack.size > 1
    if (reduceMotion) {
        BackHandler(enabled = viewBackEnabled) {
            navigationVersion++
            returnedFromDay = viewStack.last() == CalendarView.Day
            viewStack = viewStack.dropLast(1)
        }
    } else {
        PredictiveBackHandler(enabled = viewBackEnabled) { events ->
            val previous = viewStack[viewStack.size - 2]
            // Set before the seek, which already composes the previous view.
            returnedFromDay = viewStack.last() == CalendarView.Day
            try {
                events.collect { viewTransition.seekTo(it.progress, targetState = previous) }
                navigationVersion++
                viewStack = viewStack.dropLast(1)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { viewTransition.animateTo(viewTransition.currentState) }
                throw e
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Switching between the month, timeline and agenda screens is lateral
        // navigation, so it fades through rather than sliding — paging *within* a
        // view keeps the directional slide. What both views show morphs across
        // (#184): see [ViewMorphKey]. Reduced motion keeps the plain fade.
        val viewSwitch = fadeThrough()
        val morphBounds = rememberViewMorphBoundsTransform()
        val morphFade = rememberCalendarFadeSpec()
        val morphClip = remember { BelowTopBarClip() }
        val stayingPut = remember { StayingPut() }
        val chromeHost = remember { ChromeHost() }
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val density = LocalDensity.current
        val topBarBottom = TopAppBarDefaults.windowInsets.getTop(density) +
            with(density) { TopAppBarDefaults.TopAppBarExpandedHeight.toPx() }
        val timelineScroll = remember { TimelineScrollMemory() }
        // A timeline reached from another screen opens on the visible hours
        // (#322); only a switch between timeline views carries the scroll over.
        val timelineZoom = LocalTimelineZoom.current
        LaunchedEffect(view.screen) {
            if (view.screen != ViewScreen.Timeline && timelineZoom.hours.enabled) {
                timelineScroll.midnightY = null
            }
        }
        CompositionLocalProvider(
            LocalEventMove provides moveScope,
            LocalTimelineScroll provides timelineScroll,
            LocalViewFocus provides focus,
            // The failure state's way out when every calendar is switched off
            // (#239); nothing else in the calendar surfaces navigates here.
            LocalManageCalendars provides remember { { showCalendars = true } },
        ) {
        // One drawer, top bar and FAB for every screen (#184): a switch changes
        // what they say, from what the screen on show publishes, rather than
        // swapping them for another screen's.
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Open only via the menu button where edge-swipe would fight paging;
            // the agenda has no paging to fight.
            gesturesEnabled = drawerState.isOpen || view.screen == ViewScreen.Agenda,
            drawerContent = {
                CalendarDrawer(
                    currentView = view,
                    currentDate = chromeHost.chrome?.drawerDate ?: focus.date,
                    drawerState = drawerState,
                    viewOrder = drawerViewOrder,
                    onSelectView = { target ->
                        // Once the drawer is out of the way, so the switch is seen whole.
                        val requestVersion = ++navigationVersion
                        switchScope.launch {
                            drawerState.close()
                            if (navigationVersion == requestVersion) onSelectView(target)
                        }
                    },
                    onJumpToDate = { target ->
                        chromeHost.actions?.onJumpToDate?.invoke(target)
                        switchScope.launch { drawerState.close() }
                    },
                    onSettings = {
                        onOpenSettings()
                        switchScope.launch { drawerState.close() }
                    },
                )
            },
        ) {
        Scaffold(
            topBar = {
                CalendarTopBar(
                    host = chromeHost,
                    selectedView = view,
                    quickSwitchViews = quickSwitchViews,
                    onCycleView = { onSelectView(viewStack.last().next(quickSwitchViews)) },
                    onOpenDrawer = { switchScope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    showTodayButton = todayInToolbar,
                )
            },
            floatingActionButton = {
                // While an event is selected the FAB is its action bar's toggle (#141).
                val fabSelection = rememberFabSelection(
                    host = chromeHost,
                    viewModel = eventActions,
                    onEdit = onEditEvent,
                    onDuplicate = { form ->
                        // A fresh event, like a duplicate from the detail screen (#52).
                        openImportForm(form, ImportSource.Insert)
                    },
                    chipShown = moveOutcome != null || selectionFailure != null,
                    onDismissChip = {
                        reschedule.consumeOutcome()
                        eventActions.consumeFailure()
                    },
                )
                CalendarFab(chromeHost, todayInToolbar, fabSelection)
            },
        ) { innerPadding ->
        SharedTransitionLayout(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
.onGloballyPositioned { morphClip.top = topBarBottom - it.positionInWindow().y },
        ) {
        // Keyed on the screen, not the view: the day, multi-day and week views
        // are one timeline that resizes between them itself (#184), so switching
        // among them must not replay this transition.
        rememberTransition(viewTransition, label = "view-switch").AnimatedContent(
            transitionSpec = { viewSwitch },
            contentKey = { it.screen },
        ) { currentView ->
            val screen = currentView.screen
            val sharedScope = this@SharedTransitionLayout
            val visibilityScope = this@AnimatedContent
            val viewMorph = remember(reduceMotion, sharedScope, visibilityScope, morphBounds, morphFade, morphClip, stayingPut) {
                if (reduceMotion) null else ViewMorphScope(sharedScope, visibilityScope, morphBounds, morphFade, morphClip, stayingPut)
            }
            CompositionLocalProvider(
                LocalViewMorph provides viewMorph,
                LocalChromeSlot provides ChromeSlot(chromeHost, current = screen == view.screen),
            ) {
            when (screen) {
                ViewScreen.Timeline -> TimelineScreen(
                    view = currentView,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    onCreateDraft = onCreateDraft,
                )
                ViewScreen.Month -> MonthScreen(
                    onOpenDay = onOpenDay,
                    onOpenWeek = onOpenWeek,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                )
                ViewScreen.Agenda -> AgendaScreen(
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    quickSwitchViews = quickSwitchViews,
                    restorePosition = returnedFromDay,
                )
            }
            }
        }
        }
        }
        }
        }

        // Scope prompt + confirmation/undo snackbar for a dropped event, declared
        // right after the calendar views so later overlays cover it.
        EventMoveHost(
            reschedule,
            modifier = Modifier.fillMaxSize(),
            actionFailure = eventActionFailureMessage(selectionFailure),
            onActionFailureShown = eventActions::consumeFailure,
        )

        // The delete prompt and failure chip of a selected event's actions (#141).
        EventSelectionHost(host = chromeHost, viewModel = eventActions)

        // Search overlay — below detail/edit in the Box so a tapped result's
        // detail screen draws on top, and closing it returns to the results.
        SlideOverlay(visible = showSearch) {
            SearchScreen(
                onBack = { showSearch = false },
                onEventClick = onEventClick,
                visible = showSearch,
            )
        }

        val shownDetailKey = rememberHeld(detailKey)
        SlideOverlay(visible = detailKey != null) {
            shownDetailKey?.let { key ->
                EventDetailScreen(
                    eventId = key[0],
                    beginMillis = key[1],
                    endMillis = key[2],
                    onBack = { detailKey = null },
                    onEdit = { editKey = key },
                    onDuplicate = { form ->
                        // Reuse the prefilled-create overlay: a duplicate is a
                        // fresh event, so it applies the default reminder like an
                        // in-app new event (#52).
                        openImportForm(form, ImportSource.Insert)
                        detailKey = null
                    },
                    visible = detailKey != null,
                )
            }
        }

        // Event form (v1.2) — full-screen destination, slides over the calendar.
        val shownCreate = rememberHeld(createRequest)
        SlideOverlay(visible = createRequest != null, onHidden = createViewModel::reset) {
            shownCreate?.let { create ->
                key(create) {
                    EventEditScreen(
                        initialDateIso = create.dateIso,
                        initialStartMinutes = create.startMinutes,
                        initialDraft = create.draftMillis,
                        onClose = { createRequest = null },
                        onSaved = { createRequest = null },
                        onManageCalendars = { showCalendars = true },
                        visible = createRequest != null,
                        viewModel = createViewModel,
                    )
                }
            }
        }

        // Edit form (v1.3) — slides over the detail screen.
        val shownEditKey = rememberHeld(editKey)
        SlideOverlay(visible = editKey != null, onHidden = editViewModel::reset) {
            shownEditKey?.let { key ->
                EventEditScreen(
                    initialDateIso = null,
                    editKey = key,
                    onClose = { editKey = null },
                    onSaved = {
                        editKey = null
                        detailKey = null
                    },
                    onManageCalendars = { showCalendars = true },
                    onDuplicate = { form ->
                        openImportForm(form, ImportSource.Insert)
                    },
                    // Opened straight from a tap (#178) there's no detail screen
                    // below to show why, so open it.
                    onLoadFailed = {
                        editKey = null
                        if (detailKey == null) detailKey = key
                    },
                    visible = editKey != null,
                    viewModel = editViewModel,
                )
            }
        }

        // Settings (M4) — full-screen destination, slides over the calendar.
        SlideOverlay(visible = showSettings) {
            SettingsScreen(
                onBack = { showSettings = false },
                onManageCalendars = { showCalendars = true },
                onOpenBackup = { showBackup = true },
                visible = showSettings,
            )
        }

        // Import flow for an opened/received .ics file. A single event routes
        // into the create form (prefilled, for review); many open the picker.
        importUri?.let { uri ->
            ImportScreen(
                uri = uri,
                session = importSession,
                forceMany = importForceMany,
                onClose = {
                    importUri = null
                    // Back to the surface the restore started from, ready for
                    // the next file.
                    showCalendars = calendarsAfterImport
                    showBackup = backupAfterImport
                    calendarsAfterImport = false
                    backupAfterImport = false
                },
                onManageCalendars = {
                    // The manager is where this leads, so it must not be
                    // reopened underneath on close.
                    calendarsAfterImport = false
                    backupAfterImport = false
                    showCalendars = true
                },
                onOpenSingle = { form ->
                    importUri = null
                    calendarsAfterImport = false
                    backupAfterImport = false
                    openImportForm(form, ImportSource.File)
                },
            )
        }
        importForm?.let { form ->
            key(importFormId) {
                EventEditScreen(
                    initialDateIso = null,
                    initialForm = form,
                    initialFormSource = importFormSource,
                    onClose = ::closeImportForm,
                    onSaved = ::closeImportForm,
                    onManageCalendars = { showCalendars = true },
                    leaveRequested = pendingInsert != null,
                    onLeaveDeclined = { pendingInsert = null },
                    viewModel = importViewModel,
                )
            }
        }

        // Declared last so it covers every overlay that can open it: Settings,
        // both event forms, and the .ics import picker (#76).
        SlideOverlay(visible = showCalendars) {
            CalendarsScreen(
                onBack = { showCalendars = false },
                onOpenBackup = { showBackup = true },
                visible = showCalendars,
            )
        }

        // Backup & restore — over the manager, since the manager links into it.
        SlideOverlay(visible = showBackup) {
            // Settings can open this screen without the manager underneath, so
            // its failure states' "Manage calendars" way out has to open the
            // manager rather than just pop — popping alone lands on Settings
            // (#304). Coming from the manager the flag is already set, so this
            // is the same pop-back there.
            CompositionLocalProvider(
                LocalManageCalendars provides {
                    showCalendars = true
                    showBackup = false
                },
            ) {
                BackupScreen(
                    onBack = { showBackup = false },
                    // Restore runs the normal .ics import, and both this screen
                    // and the manager that can have opened it are declared above
                    // the import overlays — so both have to step aside.
                    onImport = {
                        importUri = it
                        importForceMany = true
                        importSession++
                        backupAfterImport = true
                        calendarsAfterImport = showCalendars
                        showBackup = false
                        showCalendars = false
                    },
                    visible = showBackup,
                )
            }
        }
    }
}

/** Persists the view back stack across config change / process death by ordinal. */
private val viewStackSaver = listSaver<List<CalendarView>, Int>(
    save = { stack -> stack.map(CalendarView::ordinal) },
    restore = { ordinals -> ordinals.map { CalendarView.entries[it] } },
)

/**
 * An overlay's slide-in from the end edge, leaving from the predictive-back
 * preview (#184): shrinking on from where the gesture let go instead of
 * snapping back and sliding off.
 */
@Composable
private fun SlideOverlay(
    visible: Boolean,
    onHidden: () -> Unit = {},
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    val hidden = state.isIdle && !state.currentState
    LaunchedEffect(hidden) {
        if (hidden) onHidden()
    }
    AnimatedVisibility(
        visibleState = state,
        enter = slideInHorizontally(rememberCalendarSlideSpec()) { it } + fadeIn(),
        exit = predictiveBackExit(),
        content = content,
    )
}

/** The detail and edit overlays' key for one occurrence: eventId, begin, end. */
private fun EventInstance.occurrenceKey(): LongArray =
    longArrayOf(eventId, start.toEpochMilliseconds(), end.toEpochMilliseconds())

/**
 * A create-form open: its date, plus the tapped slot's start (minutes from
 * midnight) from a day/week grid tap, or a drafted block's start and end (#153)
 * as epoch millis. Instants rather than minutes into a day, so the form opens at
 * the times drawn even across a DST change (#387).
 */
private class CreateRequest(
    val dateIso: String,
    val startMinutes: Int?,
    val draftMillis: LongArray?,
)

private val createRequestSaver = Saver<CreateRequest?, List<Any?>>(
    save = { request -> request?.let { listOf(it.dateIso, it.startMinutes, it.draftMillis) } },
    restore = { CreateRequest(it[0] as String, it[1] as Int?, it[2] as LongArray?) },
)
