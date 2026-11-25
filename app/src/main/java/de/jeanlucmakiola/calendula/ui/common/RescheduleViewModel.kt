package de.jeanlucmakiola.calendula.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.time.ZoneSource
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.domain.canSaveApartFromSeries
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurrenceEnd
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.allowsEventMove
import de.jeanlucmakiola.calendula.domain.parseSimpleRecurrence
import de.jeanlucmakiola.calendula.domain.problems
import de.jeanlucmakiola.calendula.domain.realignRecurrence
import de.jeanlucmakiola.calendula.domain.resizedTo
import de.jeanlucmakiola.calendula.domain.endInstant
import de.jeanlucmakiola.calendula.domain.startInstant
import de.jeanlucmakiola.calendula.domain.resolvedZone
import de.jeanlucmakiola.calendula.domain.shiftedByDays
import de.jeanlucmakiola.calendula.domain.shiftedTo
import de.jeanlucmakiola.calendula.domain.toEditForm
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import javax.inject.Inject

private const val MILLIS_PER_MINUTE = 60_000
private const val MINUTES_PER_DAY = 24 * 60

/** How many drops may wait behind the one in flight before more are refused. */
private const val MAX_QUEUED = 8

/** How long after a write its event's drawn times may still be the old ones. */
private val RECENT_WRITE = 3.seconds

/** Where a dragged event should land. */
sealed interface MoveTarget {
    /** A new start instant — a timeline drag, which moves time and day at once. */
    data class Start(val instant: Instant) : MoveTarget

    /**
     * A whole-day shift, keeping the time of day — a month-grid or all-day drag.
     * A delta rather than a target date, so the screen and this view model can't
     * disagree by a day over which zone the event's first day is resolved in.
     */
    data class ByDays(val days: Int) : MoveTarget

    /**
     * New start and end instants — a timeline resize, which changes the length,
     * or an all-day event dragged into the time grid, which becomes timed.
     */
    data class Span(val start: Instant, val end: Instant) : MoveTarget
}

/**
 * One dropped event. [beginMillis]/[endMillis] are the dragged *occurrence's*
 * own times (`Instances.BEGIN`/`END`), as the detail and edit screens pass them.
 */
data class MoveRequest(
    val eventId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    val target: MoveTarget,
    /** Whether the dragged occurrence is all-day, which decides the zone its day is read in. */
    val isAllDay: Boolean,
    /** The drop's own identity, from [nextMoveToken], which the view holding its copy waits on. */
    val token: Long,
)

/**
 * A recurring drop waiting for the user to pick how far it reaches.
 * [occurrenceOnly] means a wider write would leave the rule and the series
 * anchor disagreeing — either the rule names days one moved occurrence can't
 * re-derive (`BYDAY=MO,WE`, `2TH`, …), or the shift wouldn't carry the anchor
 * across the same midnights the occurrence crossed.
 */
data class MoveScopePrompt(val occurrenceOnly: Boolean, val resize: Boolean = false)

/** The inverse of a completed move, for the undo action. */
data class MoveUndo(
    val eventId: Long,
    /** The form as it now stands (the moved state). */
    val moved: EventForm,
    /** The form as it stood before the move. */
    val restored: EventForm,
    /** The token of the drop this undoes, so the view that drew it carries the right chip back. */
    val dropToken: Long,
)

/** An undo write that just began: its own [token], and the [dropToken] of the drop it undoes. */
data class UndoStart(val token: Long, val dropToken: Long)

sealed interface MoveOutcome {
    /**
     * Written. [startMillis] is where the dragged occurrence now begins, for the
     * confirmation message, and [endMillis] where it ends — set only for a resize,
     * whose message names both; [undo] is null when the write has no clean inverse.
     */
    data class Moved(
        val startMillis: Long,
        val isAllDay: Boolean,
        val undo: MoveUndo?,
        val endMillis: Long? = null,
    ) : MoveOutcome

    data object Undone : MoveOutcome

    /** `WRITE_CALENDAR` was revoked between the drop and the provider call. */
    data object WriteDenied : MoveOutcome

    /** The event vanished (sync, another device) between the read and the write. */
    data object Gone : MoveOutcome

    /** Drops that waited behind a scope dialog, cancelled rather than run after it (#494). */
    data object Skipped : MoveOutcome

    /** More drops waiting than the queue holds; this one was not taken. */
    data object QueueFull : MoveOutcome

    /**
     * The drop's recurring occurrence may no longer be where it was picked up —
     * its series was just written — and can't be followed there, so it is not
     * written (#494).
     */
    data object Stale : MoveOutcome

    /** The drop would push the series past its own `UNTIL`, generating nothing. */
    data object BlockedSeriesEnd : MoveOutcome

    /**
     * A cancelled series only moves as a whole, and this drop would change which
     * days it falls on (#388).
     */
    data object BlockedCancelledSeries : MoveOutcome

    data object Failed : MoveOutcome
}

/**
 * Writes a drag-and-drop reschedule (#68) through the same repository calls the
 * edit screen's save uses, so recurring writes, reminder reconciliation and
 * attendee preservation behave identically. Hosted at `CalendarHost` so one
 * instance serves every calendar view and survives view switches. The full
 * prefilled form is carried through the write — a stripped one would wipe the
 * reminders and attendees the occurrence-exception path reconciles.
 */
@HiltViewModel
class RescheduleViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val zoneSource: ZoneSource,
) : ViewModel() {

    private val _scopePrompt = MutableStateFlow<MoveScopePrompt?>(null)
    val scopePrompt: StateFlow<MoveScopePrompt?> = _scopePrompt.asStateFlow()

    private val _outcome = MutableStateFlow<MoveOutcome?>(null)
    val outcome: StateFlow<MoveOutcome?> = _outcome.asStateFlow()

    private var pending: PreparedMove? = null

    private val _inFlight = MutableStateFlow(false)

    /** True while any drop or undo runs or waits — what the confirmation chip holds for. */
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    private val _drops = MutableStateFlow(DropStates())

    /**
     * Each drop's own state, by its [MoveRequest.token]: pending from the moment
     * it is accepted until its write settles, and abandoned once it ends with
     * nothing on the target day — refused, failed, or its scope dialog dismissed.
     * Per drop, so a queued drop's held copy waits on its own write and is never
     * let go by another drop's failure (#253, #494).
     */
    val drops: StateFlow<DropStates> = _drops.asStateFlow()

    private val _undoStarted = MutableStateFlow<UndoStart?>(null)

    /**
     * An undo write the moment it begins, before the provider has anything to
     * re-read, so the view that drew the drop can carry its chip back rather than
     * let it reappear on the old day. Its fate then reads off [drops].
     */
    val undoStarted: StateFlow<UndoStart?> = _undoStarted.asStateFlow()

    /**
     * Set while a drop is being prepared, waits on its scope dialog, or is
     * written. Drops arriving in that window wait in [queue] and run in order.
     */
    private var busy = false
        set(value) {
            field = value
            _inFlight.value = value
        }

    /** Whether a move has been announced since the pipeline was last idle — see [report]. */
    private var movedThisRun = false

    /** Outcomes waiting for the chip, shown one after another as each is consumed. */
    private val notices = ArrayDeque<MoveOutcome>()

    /**
     * When each event's series (by id) or single occurrence (by id and begin)
     * was last written. The grid draws the old times until it re-reads, so a
     * drop on a recurring event written moments ago may carry stale times (#494).
     */
    private val written = mutableMapOf<Pair<Long, Long?>, TimeMark>()

    /** Drops that arrived while another was in flight, oldest first (#494). */
    private val queue = ArrayDeque<MoveRequest>()

    /**
     * The calendars whose events may be dragged. Nothing below the UI guards
     * this, so a block outside this set registers no drag gesture at all.
     */
    val movableCalendarIds: StateFlow<Set<Long>> = repository.calendars()
        .map { calendars -> calendars.filter { it.allowsEventMove }.map { it.id }.toSet() }
        .catch { emit(emptySet<Long>()) }
        .flowOn(io)
        .stateWhileSubscribed(
            scope = viewModelScope,
            initialValue = emptySet(),
        )

    /** Everything a drop needs, resolved before anything is written. */
    private data class PreparedMove(
        val request: MoveRequest,
        val original: EventForm,
        val updated: EventForm,
        /** True for a series master with a rule — an exception row is not one. */
        val isRecurring: Boolean,
        /** False when the rule names days this move can't re-derive. */
        val canRealign: Boolean,
        /** The series row's DTSTART date after the move — what its `UNTIL` must clear. */
        val newAnchorDate: LocalDate,
    ) {
        /** A timed event's new length, rather than an all-day one dragged into the grid. */
        val isResize: Boolean get() = request.target is MoveTarget.Span && !original.isAllDay
    }

    /**
     * Take a drop: run it now, or queue it behind the ones in flight. False means
     * nothing will be written — the queue is full — and the drop is marked
     * abandoned in [drops], so its held copy is let go by the one signal rather
     * than by every caller remembering to.
     *
     * The Boolean is diagnostic — the tests read it, [EventMoveScope] does not
     * hand it on. It answers before the write is resolved anyway.
     */
    fun move(request: MoveRequest): Boolean {
        if (queue.size >= MAX_QUEUED) {
            _drops.update { it.ended(request.token, landed = false) }
            report(MoveOutcome.QueueFull)
            return false
        }
        if (_scopePrompt.value != null) {
            _drops.update { it.ended(request.token, landed = false) }
            report(MoveOutcome.Skipped)
            return false
        }
        _drops.update { it.started(request.token) }
        if (busy) queue.addLast(request) else run(request)
        return true
    }

    private fun run(request: MoveRequest) {
        busy = true
        viewModelScope.launch {
            val prepared = prepare(request)
            if (prepared == null) {
                settle(request.token, landed = false)
                return@launch
            }
            // A cancelled series only moves as a whole (#388).
            val seriesOnly = !prepared.original.canSaveApartFromSeries()
            if (!prepared.isRecurring || (seriesOnly && prepared.canRealign)) {
                settle(request.token, write(prepared, RecurringWriteScope.AllEvents))
            } else if (seriesOnly) {
                report(MoveOutcome.BlockedCancelledSeries)
                settle(request.token, landed = false)
            } else {
                // Still busy: the scope dialog is now the thing in flight. Drops
                // waiting behind it are cancelled, not held behind a question
                // about another event.
                skipQueued()
                pending = prepared
                _scopePrompt.value = MoveScopePrompt(
                    occurrenceOnly = !prepared.canRealign,
                    resize = prepared.isResize,
                )
            }
        }
    }

    /** Answer the scope dialog. */
    fun moveWithScope(scope: RecurringWriteScope) {
        val prepared = pending ?: return
        // Guard against the dialog ever offering a scope the rule can't carry.
        if (!prepared.canRealign && scope != RecurringWriteScope.ThisEvent) return
        pending = null
        _scopePrompt.value = null
        viewModelScope.launch {
            settle(prepared.request.token, write(prepared, scope))
        }
    }

    /** Dismiss the scope dialog without writing. */
    fun cancelScope() {
        val prepared = pending ?: return
        pending = null
        _scopePrompt.value = null
        settle(prepared.request.token, landed = false)
    }

    /** End the drop [token], then run the next queued one, if any. */
    private fun settle(token: Long, landed: Boolean) {
        _drops.update { it.ended(token, landed) }
        // Straight on to the next, so busy never reads false between two drops.
        val next = queue.removeFirstOrNull()
        if (next != null) {
            run(next)
        } else {
            busy = false
            movedThisRun = false
        }
    }

    private fun skipQueued() {
        if (queue.isEmpty()) return
        val skipped = queue.map { it.token }
        queue.clear()
        _drops.update { states -> skipped.fold(states) { acc, token -> acc.ended(token, landed = false) } }
        report(MoveOutcome.Skipped)
    }

    /**
     * Queue [outcome] for the chip. While drops run back to back only the first
     * move is announced, so its Undo stays on offer; every failure gets its own
     * turn after it, since the grid shows what moved but only the chip says what
     * didn't. A later write to the announced move's event takes that Undo away,
     * as putting the event back would undo the later move too.
     */
    private fun report(outcome: MoveOutcome, eventId: Long? = null) {
        if (outcome is MoveOutcome.Moved) {
            if (movedThisRun) {
                dropUndoFor(eventId)
                return
            }
            movedThisRun = true
        } else if (outcome == (notices.lastOrNull() ?: _outcome.value)) {
            return
        }
        if (_outcome.value == null) _outcome.value = outcome else notices.addLast(outcome)
    }

    private fun dropUndoFor(eventId: Long?) {
        fun MoveOutcome.withoutUndo(): MoveOutcome =
            if (this is MoveOutcome.Moved && undo?.eventId == eventId) copy(undo = null) else this
        _outcome.value = _outcome.value?.withoutUndo()
        val rest = notices.map { it.withoutUndo() }
        notices.clear()
        notices.addAll(rest)
    }

    private fun markWritten(eventId: Long, beginMillis: Long?) {
        written[eventId to beginMillis] = TimeSource.Monotonic.markNow()
    }

    private fun writtenRecently(eventId: Long, beginMillis: Long): Boolean {
        written.entries.removeAll { it.value.elapsedNow() > RECENT_WRITE }
        return (eventId to null) in written || (eventId to beginMillis) in written
    }

    /**
     * Put a completed move back where it came from. The outcome stands until the
     * inverse write reports back, so the one chip changes what it says rather
     * than closing and reopening. False when another write is already running —
     * the chip hides its action for that window, so this is the backstop. An
     * undo is a journey of its own: [undoStarted] names its token, and [drops]
     * reports whether it landed.
     */
    fun undo(undo: MoveUndo): Boolean {
        if (busy) return false
        val token = nextMoveToken()
        _drops.update { it.started(token) }
        busy = true
        _undoStarted.value = UndoStart(token, undo.dropToken)
        markWritten(undo.eventId, null)
        viewModelScope.launch {
            _outcome.value = try {
                repository.updateEvent(undo.eventId, undo.moved, undo.restored)
                MoveOutcome.Undone
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                MoveOutcome.WriteDenied
            } catch (e: NoSuchEventException) {
                MoveOutcome.Gone
            } catch (e: Exception) {
                MoveOutcome.Failed
            }
            settle(token, landed = _outcome.value is MoveOutcome.Undone)
        }
        return true
    }

    /** Clear the outcome once the screen has shown it, bringing on the next one waiting. */
    fun consumeOutcome() {
        _outcome.value = notices.removeFirstOrNull()
    }

    private suspend fun prepare(dropped: MoveRequest): PreparedMove? {
        val zone = zoneSource.current()
        val detail = try {
            repository.eventDetail(dropped.eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchEventException) {
            report(MoveOutcome.Gone)
            return null
        } catch (e: Exception) {
            report(MoveOutcome.Failed)
            return null
        }
        // The drop carries the times the grid drew, which an earlier write may
        // have moved on since (#494).
        val request = if (detail.rrule == null || detail.isException) {
            followed(dropped, detail, zone)
        } else if (writtenRecently(dropped.eventId, dropped.beginMillis)) {
            report(MoveOutcome.Stale)
            return null
        } else {
            dropped
        }

        val original = detail.toEditForm(request.beginMillis, request.endMillis, zone)
        val shifted = when (val target = request.target) {
            is MoveTarget.Start -> original.shiftedTo(target.instant, zone)
            is MoveTarget.ByDays -> original.shiftedByDays(target.days, zone)
            is MoveTarget.Span -> original.resizedTo(target.start, target.end, zone)
        }
        // A zero-distance drop is not a write.
        if (shifted == original) return null

        // The UNTIL check is deferred to the write, which knows how far the move
        // reaches and so which date has to clear it.
        if ((shifted.problems(zone) - EventFormProblem.RecurrenceEndsBeforeStart).isNotEmpty()) {
            report(MoveOutcome.Failed)
            return null
        }

        // An exception row stands alone whatever rule a sync adapter left on it.
        val isRecurring = original.rrule != null && !detail.isException
        val movedDay = shifted.start.date != original.start.date
        // The series anchor moves by the same *wall-clock* shift as the dragged
        // occurrence, so only a whole-day shift carries it across the same number
        // of midnights whatever time of day it sits at. A same-date drag has its
        // own, narrower check — see anchorKeepsItsDay.
        val wholeDayShift = original.isAllDay || shifted.start.time == original.start.time
        val realigned = if (isRecurring && movedDay) {
            if (wholeDayShift) {
                realignRecurrence(
                    requireNotNull(original.rrule),
                    original.start.date,
                    shifted.start.date,
                )
            } else {
                null
            }
        } else {
            original.rrule
        }
        return PreparedMove(
            request = request,
            original = original,
            // A rule we can't re-derive stays verbatim: the only scope offered
            // then is the single occurrence, whose exception row carries no rule.
            updated = shifted.copy(rrule = realigned ?: original.rrule),
            isRecurring = isRecurring,
            canRealign = when {
                !isRecurring -> true
                movedDay -> realigned != null
                else -> anchorKeepsItsDay(detail, original, shifted, zone)
            },
            // Where the series row's own DTSTART lands, given the anchor moves by
            // the same shift. Only read under canRealign, which is what
            // guarantees the anchor really does travel this many days.
            newAnchorDate = anchorDate(detail, original, zone)
                .plus(shifted.start.date.toEpochDays() - original.start.date.toEpochDays(), DateTimeUnit.DAY),
        )
    }

    /** Write [prepared] in [scope]; true when it landed. */
    private suspend fun write(prepared: PreparedMove, scope: RecurringWriteScope): Boolean {
        val request = prepared.request
        if (endsBeforeItStarts(prepared, scope)) {
            report(MoveOutcome.BlockedSeriesEnd)
            return false
        }
        val outcome = try {
            when {
                !prepared.isRecurring || scope == RecurringWriteScope.AllEvents ->
                    repository.updateEvent(request.eventId, prepared.original, prepared.updated)

                scope == RecurringWriteScope.ThisEvent ->
                    repository.updateOccurrence(
                        request.eventId,
                        request.beginMillis,
                        prepared.original,
                        prepared.updated,
                    )

                else -> repository.updateEventFromOccurrence(
                    eventId = request.eventId,
                    beginMillis = request.beginMillis,
                    original = prepared.original,
                    updated = prepared.updated,
                )
            }
            MoveOutcome.Moved(
                startMillis = movedStartMillis(prepared),
                isAllDay = prepared.updated.isAllDay,
                undo = undoFor(prepared, scope),
                endMillis = resizedEndMillis(prepared),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            MoveOutcome.WriteDenied
        } catch (e: NoSuchEventException) {
            MoveOutcome.Gone
        } catch (e: Exception) {
            MoveOutcome.Failed
        }
        if (outcome is MoveOutcome.Moved) {
            markWritten(request.eventId, request.beginMillis.takeIf { scope == RecurringWriteScope.ThisEvent && prepared.isRecurring })
        }
        report(outcome, request.eventId)
        return outcome is MoveOutcome.Moved
    }

    /**
     * A one-off [dropped] rebased onto where its event now is, so the last drop
     * wins: absolute targets stand as they are, and a whole-day shift is re-aimed
     * at the day it was dropped on. Unchanged when the event hasn't moved.
     */
    private fun followed(dropped: MoveRequest, detail: EventDetail, zone: TimeZone): MoveRequest {
        val now = detail.instance
        val target = when (val target = dropped.target) {
            is MoveTarget.ByDays -> {
                // Each day read the way its own kind anchors it: all-day at UTC.
                val aimed = Instant.fromEpochMilliseconds(dropped.beginMillis)
                    .toLocalDateTime(if (dropped.isAllDay) TimeZone.UTC else zone).date
                    .plus(target.days, DateTimeUnit.DAY)
                val from = now.start.toLocalDateTime(if (now.isAllDay) TimeZone.UTC else zone).date
                MoveTarget.ByDays((aimed.toEpochDays() - from.toEpochDays()).toInt())
            }
            else -> target
        }
        return dropped.copy(
            beginMillis = now.start.toEpochMilliseconds(),
            endMillis = now.end.toEpochMilliseconds(),
            target = target,
            isAllDay = now.isAllDay,
        )
    }

    /**
     * Whether this write would leave a rule whose `UNTIL` precedes the first day
     * it now applies to — the provider then generates nothing at all. Which date
     * has to clear `UNTIL` depends on the scope: a whole-series move carries the
     * series *anchor*, a split starts at the moved occurrence, and a single
     * occurrence becomes an exception row that no `UNTIL` constrains.
     */
    private fun endsBeforeItStarts(prepared: PreparedMove, scope: RecurringWriteScope): Boolean {
        if (!prepared.isRecurring || scope == RecurringWriteScope.ThisEvent) return false
        val rule = prepared.updated.rrule ?: return false
        val end = parseSimpleRecurrence(rule)?.end as? RecurrenceEnd.Until ?: return false
        val firstDay = if (scope == RecurringWriteScope.AllEvents) {
            prepared.newAnchorDate
        } else {
            prepared.updated.start.date
        }
        return end.date < firstDay
    }

    /**
     * Whether a same-date drag leaves the series anchor on its own day too. The
     * anchor moves by the same wall-clock delta as the occurrence, and normally
     * shares its time of day — but a row with no `EVENT_TIMEZONE` resolves the
     * two in zones that can sit a DST hour apart, so a near-midnight drag could
     * carry the anchor across a midnight the occurrence never crossed and leave
     * `BYDAY` naming the wrong weekday.
     */
    private fun anchorKeepsItsDay(
        detail: EventDetail,
        original: EventForm,
        shifted: EventForm,
        zone: TimeZone,
    ): Boolean {
        val anchorZone = if (original.isAllDay) TimeZone.UTC else original.resolvedZone(zone)
        val anchorMinute = detail.instance.start.toLocalDateTime(anchorZone)
            .time.toMillisecondOfDay() / MILLIS_PER_MINUTE
        // An all-day form's time is a placeholder; its anchor sits at midnight.
        val fromMillis = if (original.isAllDay) 0 else original.start.time.toMillisecondOfDay()
        val delta = (shifted.start.time.toMillisecondOfDay() - fromMillis) / MILLIS_PER_MINUTE
        return anchorMinute + delta in 0 until MINUTES_PER_DAY
    }

    /**
     * The series row's own start date — for a recurring master, `EventDetail`
     * carries the row's DTSTART rather than the tapped occurrence's. Read in the
     * same anchoring the write path uses: UTC for an all-day series.
     */
    private fun anchorDate(detail: EventDetail, original: EventForm, zone: TimeZone): LocalDate =
        detail.instance.start
            .toLocalDateTime(if (original.isAllDay) TimeZone.UTC else original.resolvedZone(zone))
            .date

    /**
     * Undo is offered only where the inverse is one symmetric write: a
     * non-recurring event, and a whole-series move. "This event" leaves an
     * exception row behind and "this and following" splits the series with an
     * UNTIL truncation — neither is undone by shifting back.
     */
    private fun undoFor(prepared: PreparedMove, scope: RecurringWriteScope): MoveUndo? =
        if (!prepared.isRecurring || scope == RecurringWriteScope.AllEvents) {
            MoveUndo(
                eventId = prepared.request.eventId,
                moved = prepared.updated,
                restored = prepared.original,
                dropToken = prepared.request.token,
            )
        } else {
            null
        }

    /** Where a resized occurrence now ends; null for a move, which keeps its length. */
    private fun resizedEndMillis(prepared: PreparedMove): Long? {
        if (!prepared.isResize) return null
        val form = prepared.updated
        return form.endInstant(form.resolvedZone(zoneSource.current())).toEpochMilliseconds()
    }

    /**
     * Where the dragged occurrence now begins, in the same anchoring the views
     * read dates back in: UTC midnight for an all-day event.
     */
    private fun movedStartMillis(prepared: PreparedMove): Long {
        val form = prepared.updated
        return if (form.isAllDay) {
            form.start.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        } else {
            form.startInstant(form.resolvedZone(zoneSource.current())).toEpochMilliseconds()
        }
    }

}
