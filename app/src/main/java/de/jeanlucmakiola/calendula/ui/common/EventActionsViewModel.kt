package de.jeanlucmakiola.calendula.ui.common

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.time.ZoneSource
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.atOccurrence
import de.jeanlucmakiola.calendula.domain.toDuplicateForm
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/** A delete waiting on its confirmation dialog. */
data class DeletePrompt(val event: EventInstance, val isRecurring: Boolean)

/** How an action on a selected event ended, when it has something to say. */
enum class EventActionFailure { LoadFailed, ShareFailed, DeleteFailed, WriteDenied, Gone }

/** Duplicate, share and delete for a selected event (#141), loading it on demand. */
@HiltViewModel
class EventActionsViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val icsExporter: IcsExporter,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val zoneSource: ZoneSource,
) : ViewModel() {

    /** Every calendar by id, for what a selected event's bar may offer. */
    val calendars: StateFlow<Map<Long, CalendarSource>> = repository.calendars()
        .map { list -> list.associateBy { it.id } }
        .catch { emit(emptyMap()) }
        .flowOn(io)
        .stateWhileSubscribed(
            scope = viewModelScope,
            initialValue = emptyMap(),
        )

    private val _deletePrompt = MutableStateFlow<DeletePrompt?>(null)
    val deletePrompt: StateFlow<DeletePrompt?> = _deletePrompt.asStateFlow()

    private val _failure = MutableStateFlow<EventActionFailure?>(null)
    val failure: StateFlow<EventActionFailure?> = _failure.asStateFlow()

    /** A create form copying [event], or null when it can't be read. */
    suspend fun duplicateForm(event: EventInstance): EventForm? {
        val detail = load(event) ?: return null
        val canModify = calendars.value[event.calendarId]?.canModifyContents == true
        return detail.toDuplicateForm(canModify, zoneSource.current())
    }

    /** A staged `.ics` for [event], or null — reported — when it can't be built. */
    suspend fun shareUri(event: EventInstance): Uri? {
        val uri = load(event)?.let { withContext(io) { icsExporter.stageEventShare(it) } }
        if (uri == null && _failure.value == null) _failure.value = EventActionFailure.ShareFailed
        return uri
    }

    /** Ask to delete [event]; the dialog it needs depends on whether it recurs. */
    fun requestDelete(event: EventInstance) {
        viewModelScope.launch {
            val detail = load(event) ?: return@launch
            _deletePrompt.value = DeletePrompt(event, isRecurring = !detail.rrule.isNullOrBlank())
        }
    }

    fun confirmDelete(scope: RecurringWriteScope) {
        val prompt = _deletePrompt.value ?: return
        _deletePrompt.value = null
        viewModelScope.launch {
            val event = prompt.event
            _failure.value = when (repository.tryDeleteEvent(event.eventId, event.start.toEpochMilliseconds(), scope)) {
                DeleteOutcome.Deleted -> null
                DeleteOutcome.Gone -> EventActionFailure.Gone
                DeleteOutcome.NeedsPermission -> EventActionFailure.WriteDenied
                DeleteOutcome.Failed -> EventActionFailure.DeleteFailed
            }
        }
    }

    fun cancelDelete() {
        _deletePrompt.value = null
    }

    /** The chooser itself couldn't open — nothing on the device takes an `.ics`. */
    fun reportShareFailed() {
        _failure.value = EventActionFailure.ShareFailed
    }

    fun consumeFailure() {
        _failure.value = null
    }

    /** Whether [calendarId] takes writes, asking the provider while [calendars] hasn't loaded yet. */
    suspend fun canModify(calendarId: Long): Boolean {
        val calendar = calendars.value[calendarId] ?: try {
            repository.calendars().first().firstOrNull { it.id == calendarId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return calendar?.canModifyContents == true
    }

    private suspend fun load(event: EventInstance): EventDetail? = try {
        // The row holds the series start; the actions work on this occurrence.
        repository.eventDetail(event.eventId)
            .atOccurrence(event.start.toEpochMilliseconds(), event.end.toEpochMilliseconds())
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoSuchEventException) {
        _failure.value = EventActionFailure.Gone
        null
    } catch (e: Exception) {
        _failure.value = EventActionFailure.LoadFailed
        null
    }
}
