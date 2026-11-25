package de.jeanlucmakiola.calendula.widget.config

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.calendula.widget.DaysWidgetData
import de.jeanlucmakiola.calendula.widget.WidgetLoadContext
import de.jeanlucmakiola.calendula.widget.WidgetSettings
import de.jeanlucmakiola.calendula.widget.loadDays
import de.jeanlucmakiola.calendula.widget.weekOf
import de.jeanlucmakiola.calendula.widget.upcoming.loadUpcoming
import de.jeanlucmakiola.calendula.widget.today.loadTodayList
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.widget.countdown.CountdownData
import de.jeanlucmakiola.calendula.widget.countdown.CountdownOptions
import de.jeanlucmakiola.calendula.widget.countdown.countdownOptions
import de.jeanlucmakiola.calendula.widget.countdown.loadCountdown
import de.jeanlucmakiola.calendula.widget.countdown.putCountdownOptions
import de.jeanlucmakiola.calendula.widget.split.MonthDayData
import de.jeanlucmakiola.calendula.widget.split.loadMonthDay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import de.jeanlucmakiola.calendula.widget.WidgetOption
import de.jeanlucmakiola.calendula.widget.hiddenCalendars
import de.jeanlucmakiola.calendula.widget.putHiddenCalendars
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.widget.AgendaWidgetData
import de.jeanlucmakiola.calendula.widget.AgendaWidgetOptions
import de.jeanlucmakiola.calendula.widget.MonthWidgetSource
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.loadAgendaWidgetData
import de.jeanlucmakiola.calendula.widget.loadThemedMonthSource
import de.jeanlucmakiola.calendula.widget.monthIndexOf
import de.jeanlucmakiola.calendula.widget.monthWindowCenter
import de.jeanlucmakiola.calendula.widget.agendaWidgetOptions
import de.jeanlucmakiola.calendula.widget.putAgendaOptions
import de.jeanlucmakiola.calendula.widget.putAppearance
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetSettings
import de.jeanlucmakiola.calendula.widget.widgetState
import de.jeanlucmakiola.floret.glance.updateWithRefresh
import de.jeanlucmakiola.floret.prefs.ThemeMode
import javax.inject.Inject
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** What the preview draws, loaded with the draft's options. */
sealed interface WidgetPreviewData {
    data class Agenda(val data: AgendaWidgetData) : WidgetPreviewData
    data class Month(val source: MonthWidgetSource, val index: Int) : WidgetPreviewData
    data class Today(val today: LocalDate) : WidgetPreviewData
    data class Days(val data: DaysWidgetData) : WidgetPreviewData
    data class Countdown(val data: CountdownData) : WidgetPreviewData
    data class MonthDay(val data: MonthDayData, val index: Int) : WidgetPreviewData
}

/** The options being edited, applied to the preview as they change and written on save. */
data class WidgetDraft(
    val appearance: WidgetAppearance,
    /** The content options; stored only for a kind that [WidgetKind.hasContentOptions]. */
    val content: AgendaWidgetOptions,
    val hiddenCalendars: Set<Long>,
    val countdown: CountdownOptions = CountdownOptions(),
)

/** One event or series the countdown can be pinned to, at its next occurrence. */
data class CountdownCandidate(val event: EventInstance, val repeats: Boolean)

/** The countdown's event list, loaded when its picker first opens. */
sealed interface CountdownCandidates {
    data object Idle : CountdownCandidates
    data object Loading : CountdownCandidates
    data object Failed : CountdownCandidates
    data class Ready(val events: List<CountdownCandidate>) : CountdownCandidates
}

private fun WidgetDraft.loadContext(settings: WidgetSettings) = WidgetLoadContext(settings, content, hiddenCalendars)

sealed interface WidgetConfigUiState {
    data object Loading : WidgetConfigUiState

    /** The widget is gone, or isn't one of ours; the screen closes. */
    data object Missing : WidgetConfigUiState

    /** Its stored options couldn't be read. */
    data object Failed : WidgetConfigUiState

    data class Ready(
        val kind: WidgetKind,
        val draft: WidgetDraft,
        /** The calendars the app shows, for the widget's own calendar choice. */
        val calendars: List<CalendarSource>,
        val appTheme: ThemeMode,
        val weekStart: DayOfWeek,
        /** The widget's size on the home screen, which the preview is laid out at. */
        val placedSize: DpSize,
        /** Null until the first preview load lands. */
        val preview: WidgetPreviewData?,
        val candidates: CountdownCandidates = CountdownCandidates.Idle,
        /** The first preview load failed, so there's nothing to show. */
        val previewFailed: Boolean = false,
        val saving: Boolean = false,
    ) : WidgetConfigUiState {
        val appearance: WidgetAppearance get() = draft.appearance
    }
}

/** One-time outcomes of a save, for the screen that's showing when they land. */
enum class WidgetConfigEvent { SAVED, SAVE_FAILED }

/**
 * Backs [WidgetConfigActivity] (#292): the placed widget's stored options as an
 * editable draft, a preview loaded with that draft, and the save that writes it
 * into the widget's Glance state and redraws it.
 */
@HiltViewModel
class WidgetConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPrefs,
    private val calendarPrefs: CalendarPrefs,
    private val repository: CalendarRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    val appWidgetId: Int = savedState[AppWidgetManager.EXTRA_APPWIDGET_ID] ?: AppWidgetManager.INVALID_APPWIDGET_ID

    private val _state = MutableStateFlow<WidgetConfigUiState>(WidgetConfigUiState.Loading)
    val state: StateFlow<WidgetConfigUiState> = _state.asStateFlow()

    private val _events = Channel<WidgetConfigEvent>(Channel.BUFFERED)
    val events: Flow<WidgetConfigEvent> = _events.receiveAsFlow()

    private var previewJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    fun retry() {
        if (_state.value != WidgetConfigUiState.Failed) return
        _state.value = WidgetConfigUiState.Loading
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val kind = WidgetKind.of(context, appWidgetId)
        if (kind == null) {
            _state.value = WidgetConfigUiState.Missing
            return
        }
        _state.value = try {
            val stored = context.widgetState(GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId))
            val settings = prefs.widgetSettings.first()
            WidgetConfigUiState.Ready(
                kind = kind,
                draft = WidgetDraft(
                    appearance = stored.widgetAppearance,
                    content = stored.agendaWidgetOptions(settings.agendaDefaults),
                    hiddenCalendars = stored.hiddenCalendars,
                    countdown = stored.countdownOptions,
                ),
                calendars = if (WidgetOption.CALENDARS in kind.options) shownCalendars() else emptyList(),
                appTheme = settings.themeMode,
                weekStart = settings.weekStart.resolveFirstDay(widgetLocale()),
                placedSize = placedSize(kind),
                preview = null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: IllegalArgumentException) {
            // getGlanceIdBy: the widget was removed meanwhile.
            WidgetConfigUiState.Missing
        } catch (_: Exception) {
            WidgetConfigUiState.Failed
        }
        reloadPreview()
    }

    /** The size the launcher placed the widget at (portrait), or a default before it has reported one. */
    private fun placedSize(kind: WidgetKind): DpSize {
        val options = AppWidgetManager.getInstance(context)?.getAppWidgetOptions(appWidgetId)
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        if (width > 0 && height > 0) return DpSize(width.dp, height.dp)
        return kind.defaultSize
    }

    /** The calendars the app itself shows; the ones hidden there never reach a widget anyway. */
    private suspend fun shownCalendars(): List<CalendarSource> {
        val hidden = calendarPrefs.hiddenCalendarIds.first()
        return repository.calendars().first().filter { it.isVisibleInSystem && it.id !in hidden }
    }

    fun setAppearance(appearance: WidgetAppearance) = edit { it.copy(draft = it.draft.copy(appearance = appearance)) }

    fun setContent(options: AgendaWidgetOptions) {
        edit { it.copy(draft = it.draft.copy(content = options)) }
        reloadPreview()
    }

    fun setCountdown(options: CountdownOptions) {
        edit { it.copy(draft = it.draft.copy(countdown = options)) }
        reloadPreview()
    }

    /** Loads the events the countdown can be pinned to, unless they're loaded or loading. */
    fun loadCountdownCandidates() {
        val ready = _state.value as? WidgetConfigUiState.Ready ?: return
        if (ready.candidates is CountdownCandidates.Loading || ready.candidates is CountdownCandidates.Ready) return
        edit { it.copy(candidates = CountdownCandidates.Loading) }
        viewModelScope.launch {
            val candidates = try {
                val now = Clock.System.now()
                val zone = TimeZone.currentSystemDefault()
                // A year, a quarter at a time, keeping only each event's first
                // occurrence so busy series don't hold the whole year in memory.
                val first = LinkedHashMap<Long, EventInstance>()
                val repeats = HashSet<Long>()
                for (quarter in 0 until 4) {
                    val from = now + (quarter * 91).days
                    val to = if (quarter == 3) now + 365.days else now + ((quarter + 1) * 91).days
                    repository.instances(from..to).first()
                        .filter { !it.hasEnded(now, zone) }
                        .forEach { occurrence ->
                            val known = first[occurrence.eventId]
                            when {
                                known == null -> first[occurrence.eventId] = occurrence
                                known.start == occurrence.start -> Unit
                                else -> {
                                    repeats += occurrence.eventId
                                    if (occurrence.start < known.start) first[occurrence.eventId] = occurrence
                                }
                            }
                        }
                }
                CountdownCandidates.Ready(
                    first.values
                        .map { CountdownCandidate(it, repeats = it.eventId in repeats) }
                        .sortedBy { it.event.start },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CountdownCandidates.Failed
            }
            edit { it.copy(candidates = candidates) }
        }
    }

    fun setCalendarShown(id: Long, shown: Boolean) {
        edit {
            val hidden = it.draft.hiddenCalendars
            it.copy(draft = it.draft.copy(hiddenCalendars = if (shown) hidden - id else hidden + id))
        }
        reloadPreview()
    }

    private fun edit(change: (WidgetConfigUiState.Ready) -> WidgetConfigUiState.Ready) {
        _state.update { if (it is WidgetConfigUiState.Ready) change(it) else it }
    }

    private fun reloadPreview() {
        val ready = _state.value as? WidgetConfigUiState.Ready ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val preview = try {
                loadPreview(ready)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The options stay editable; the preview keeps what it had.
                edit { it.copy(previewFailed = it.preview == null) }
                return@launch
            }
            edit { it.copy(preview = preview, previewFailed = false) }
        }
    }

    private suspend fun loadPreview(ready: WidgetConfigUiState.Ready): WidgetPreviewData {
        val draft = ready.draft
        val settings = prefs.widgetSettings.first()
        return when (ready.kind) {
            WidgetKind.AGENDA ->
                WidgetPreviewData.Agenda(context.loadAgendaWidgetData(settings, draft.content, draft.hiddenCalendars))
            WidgetKind.MONTH -> {
                val index = monthIndexOf(today(systemZone()))
                val source = context.loadThemedMonthSource(monthWindowCenter(index), draft.hiddenCalendars)
                WidgetPreviewData.Month(source.content, index)
            }
            WidgetKind.QUICK_ADD, WidgetKind.DATE -> WidgetPreviewData.Today(today(systemZone()))
            WidgetKind.UPCOMING -> WidgetPreviewData.Days(context.loadUpcoming(draft.loadContext(settings)).content)
            WidgetKind.TODAY -> WidgetPreviewData.Days(context.loadTodayList(draft.loadContext(settings)).content)
            WidgetKind.MONTH_DAY -> {
                val index = monthIndexOf(today(systemZone()))
                val source = context.loadThemedMonthSource(monthWindowCenter(index), draft.hiddenCalendars)
                WidgetPreviewData.MonthDay(context.loadMonthDay(source, draft.content).content, index)
            }
            WidgetKind.COUNTDOWN ->
                WidgetPreviewData.Countdown(context.loadCountdown(draft.loadContext(settings), draft.countdown, null).content)
            WidgetKind.WEEK_STRIP -> WidgetPreviewData.Days(context.loadDays(draft.loadContext(settings), ::weekOf).content)
        }
    }

    /** Writes the draft into the widget and redraws it, then reports how that went on [events]. */
    fun save() {
        val ready = _state.value as? WidgetConfigUiState.Ready ?: return
        if (ready.saving) return
        edit { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                ready.kind.widget().updateWithRefresh(context, glanceId) { state ->
                    state.putAppearance(ready.draft.appearance)
                    if (ready.kind.hasContentOptions) state.putAgendaOptions(ready.draft.content)
                    if (WidgetOption.CALENDARS in ready.kind.options) state.putHiddenCalendars(ready.draft.hiddenCalendars)
                    if (ready.kind == WidgetKind.COUNTDOWN) state.putCountdownOptions(ready.draft.countdown)
                }
                _events.send(WidgetConfigEvent.SAVED)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IllegalArgumentException) {
                _state.value = WidgetConfigUiState.Missing
            } catch (_: Exception) {
                edit { it.copy(saving = false) }
                _events.send(WidgetConfigEvent.SAVE_FAILED)
            }
        }
    }
}
