package de.jeanlucmakiola.calendula.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.time.ZoneSource
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.atOccurrence
import de.jeanlucmakiola.calendula.domain.descriptionRichText
import de.jeanlucmakiola.calendula.domain.toDuplicateForm
import de.jeanlucmakiola.calendula.ui.common.DeleteOutcome
import de.jeanlucmakiola.calendula.ui.common.tryDeleteEvent
import de.jeanlucmakiola.calendula.ui.common.stageEventShare
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Loads a single event's detail on demand for the bottom sheet (spec S4).
 * The event id is set via [open]; the sheet observes [state].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val icsExporter: IcsExporter,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val zoneSource: ZoneSource,
) : ViewModel() {

    private val _target = MutableStateFlow<Target?>(null)
    // Bumped by retry() and by re-opening the target already shown, to re-run
    // the load without changing the target.
    private val _reload = MutableStateFlow(0)

    // Last target whose content is already on screen; a re-read of it skips the
    // Loading skeleton so the sheet doesn't blank out between two identical reads.
    private var loadedTarget: Target? = null

    private val _deleteState = MutableStateFlow<DeleteUiState>(DeleteUiState.Idle)
    val deleteState: StateFlow<DeleteUiState> = _deleteState.asStateFlow()

    val state: StateFlow<EventDetailUiState> =
        combine(_target, _reload) { target, _ -> target }
            .flatMapLatest { target ->
                if (target == null) {
                    loadedTarget = null
                    flowOf<EventDetailUiState>(EventDetailUiState.Loading)
                } else {
                    flow {
                        if (loadedTarget != target) emit(EventDetailUiState.Loading)
                        val loaded = loadDetail(target)
                        loadedTarget = target.takeIf { loaded is EventDetailUiState.Success }
                        emit(loaded)
                    }
                }
            }
            .flowOn(io)
            .stateWhileSubscribed(
                scope = viewModelScope,
                initialValue = EventDetailUiState.Loading,
            )

    /**
     * Open (or switch) to the tapped occurrence. [beginMillis]/[endMillis] are
     * the occurrence's own times (from `CalendarContract.Instances`); they
     * override the series DTSTART/DTEND so recurring events show the correct
     * date instead of the first occurrence.
     *
     * Re-opening the *same* occurrence always re-reads it. The view model
     * outlives the sheet, and a `StateFlow` conflates an identical value away,
     * so assigning the target alone would leave an edit that changed no time —
     * adding a description, say (#196) — showing the state from before the save.
     */
    fun open(eventId: Long, beginMillis: Long, endMillis: Long) {
        val target = Target(eventId, beginMillis, endMillis)
        if (_target.value == target) _reload.value += 1 else _target.value = target
    }

    /** Re-run the current load after a failure. */
    fun retry() {
        _reload.value += 1
    }

    /**
     * Delete the open event. [scope] is meaningful only for recurring events
     * (one-off events always pass [RecurringWriteScope.AllEvents]). Result
     * lands in [deleteState]; the screen consumes it via [consumeDeleteResult].
     */
    fun delete(scope: RecurringWriteScope) {
        val target = _target.value ?: return
        if (_deleteState.value == DeleteUiState.Deleting) return
        viewModelScope.launch {
            _deleteState.value = DeleteUiState.Deleting
            _deleteState.value = when (repository.tryDeleteEvent(target.eventId, target.beginMillis, scope)) {
                // Already gone is what a delete wants anyway.
                DeleteOutcome.Deleted, DeleteOutcome.Gone -> DeleteUiState.Deleted
                DeleteOutcome.NeedsPermission -> DeleteUiState.NeedsPermission
                DeleteOutcome.Failed -> DeleteUiState.Failed
            }
        }
    }

    /** Reset [deleteState] after the screen handled a terminal result. */
    fun consumeDeleteResult() {
        _deleteState.value = DeleteUiState.Idle
    }

    /**
     * Serialise the open event to a `.ics` cache file and return a shareable
     * content Uri (for an ACTION_SEND), or null when nothing is loaded or the
     * write fails. The event is exported as a one-off (see [toShareIcsEvent]).
     */
    suspend fun shareUri(): Uri? {
        val detail = (state.value as? EventDetailUiState.Success)?.detail ?: return null
        return withContext(io) { icsExporter.stageEventShare(detail) }
    }

    /**
     * A create form seeded from the open event, to save a copy as a new event
     * (#52) — see [toDuplicateForm]. Null when nothing is loaded.
     */
    fun duplicateForm(): EventForm? {
        val loaded = state.value as? EventDetailUiState.Success ?: return null
        return loaded.detail.toDuplicateForm(loaded.canModify, zoneSource.current())
    }

    private suspend fun loadDetail(target: Target): EventDetailUiState = try {
        val detail = repository.eventDetail(target.eventId)
        // An external "open event" that names no occurrence ([NO_OCCURRENCE_TIME] —
        // e.g. a bare content://.../events/<id> VIEW intent, issue #48) keeps the
        // row's own DTSTART/DTEND instead of overriding it to the epoch.
        val corrected = if (target.beginMillis == NO_OCCURRENCE_TIME) {
            detail
        } else {
            detail.atOccurrence(target.beginMillis, target.endMillis)
        }
        val calendar = repository.calendars().first()
            .firstOrNull { it.id == corrected.instance.calendarId }
        val description = corrected.description
            ?.let(::descriptionRichText)
            ?.takeIf { it.text.isNotBlank() }
        EventDetailUiState.Success(
            detail = corrected,
            calendarName = calendar?.displayName,
            canModify = calendar?.canModifyContents == true,
            isManaged = calendar?.isManaged == true,
            // Found here, off the main thread and once per load: the platform
            // matcher loads its metadata on first use.
            phoneLinks = findEventPhoneLinks(
                title = corrected.instance.title,
                location = corrected.instance.location,
                description = description?.text,
            ),
            description = description,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoSuchEventException) {
        EventDetailUiState.Failure(FailureReason.EventNotFound)
    } catch (e: SecurityException) {
        EventDetailUiState.Failure(FailureReason.PermissionRevoked)
    } catch (e: Exception) {
        EventDetailUiState.Failure(FailureReason.ProviderUnavailable)
    }

    /** A tapped occurrence: the series [eventId] plus this occurrence's own times. */
    private data class Target(val eventId: Long, val beginMillis: Long, val endMillis: Long)

    companion object {
        /**
         * Sentinel begin/end for an "open this event" that names no occurrence —
         * a bare `content://com.android.calendar/events/<id>` VIEW intent with no
         * `EXTRA_EVENT_BEGIN_TIME` (issue #48). [loadDetail] then keeps the event
         * row's own DTSTART/DTEND instead of overriding it to the epoch.
         */
        const val NO_OCCURRENCE_TIME: Long = Long.MIN_VALUE
    }
}
