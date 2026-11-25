package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.MainDispatcherExtension
import de.jeanlucmakiola.calendula.data.time.FakeZoneSource
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.testDetail
import de.jeanlucmakiola.calendula.testEvent
import de.jeanlucmakiola.calendula.testPrefs
import de.jeanlucmakiola.calendula.testRepository
import de.jeanlucmakiola.calendula.testSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The drop pipeline: which repository call a scope maps to, when a recurring drop
 * has to ask first, and every case that must refuse to write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RescheduleViewModelTest {

    @JvmField @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()
    private val dispatcher = mainDispatcher.dispatcher

    // Monday 2026-06-08, midday in the device zone — the zone the drop path
    // reads dates back in. The BYDAY assertions depend on it being a Monday.
    private val monday = LocalDate(2026, 6, 8)
    private val beginMillis = LocalDateTime(monday, LocalTime(12, 0))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
    private val endMillis = beginMillis + 3_600_000L

    private fun cal(
        id: Long,
        canModify: Boolean = true,
        managed: Boolean = false,
    ): CalendarSource = CalendarSource(
        id = id, displayName = "Cal $id", accountName = "acc@local", accountType = "LOCAL",
        color = 0xFF112233.toInt(), isVisibleInSystem = true, canModifyContents = canModify,
        isManaged = managed,
    )

    private fun detail(
        rrule: String? = null,
        isAllDay: Boolean = false,
        isException: Boolean = false,
        anchorMillis: Long = beginMillis,
        status: EventStatus = EventStatus.Confirmed,
    ): EventDetail = testDetail(
        instance = testEvent(
            id = 42L, title = "Standup",
            start = Instant.fromEpochMilliseconds(anchorMillis),
            end = Instant.fromEpochMilliseconds(anchorMillis + (endMillis - beginMillis)),
            isAllDay = isAllDay, color = 0xFF000000.toInt(),
        ),
        rrule = rrule,
        isException = isException,
        status = status,
    )

    /**
     * [present] are the events whose occurrence the provider still reports where
     * a drop picked it up; [io] a [StandardTestDispatcher] keeps a drop in flight.
     */
    private fun viewModel(
        tempDir: Path,
        fake: FakeCalendarDataSource,
        present: List<Long> = listOf(42L, 7L),
        io: CoroutineDispatcher = dispatcher,
    ): RescheduleViewModel {
        fake.occurrencesAt(present)
        val repo = testRepository(
            fake, tempDir, io,
            prefs = testPrefs(tempDir, CoroutineScope(dispatcher)),
            settings = testSettings(tempDir, CoroutineScope(dispatcher)),
        )
        return RescheduleViewModel(repo, io, FakeZoneSource())
    }

    private fun slowViewModel(tempDir: Path, fake: FakeCalendarDataSource): RescheduleViewModel =
        viewModel(tempDir, fake, io = StandardTestDispatcher(dispatcher.scheduler))

    /** Every query answers with an occurrence of each of [ids] exactly at the queried span. */
    private fun FakeCalendarDataSource.occurrencesAt(ids: List<Long>) {
        instancesResult = { begin, end ->
            ids.map { id ->
                testEvent(
                    id = id,
                    start = Instant.fromEpochMilliseconds(begin),
                    end = Instant.fromEpochMilliseconds(end),
                )
            }
        }
    }

    private val RescheduleViewModel.abandonedCount: Int get() = drops.value.abandoned.size

    private fun CoroutineScope.activate(vm: RescheduleViewModel): Job =
        launch { vm.movableCalendarIds.collect {} }

    /** A drop two days later, expressed the way the month grid expresses one. */
    private fun toWednesday() = MoveRequest(
        eventId = 42L,
        beginMillis = beginMillis,
        endMillis = endMillis,
        target = MoveTarget.ByDays(2),
        isAllDay = false,
        token = nextMoveToken(),
    )

    /** A drop an hour later, expressed the way a timeline drag expresses one. */
    private fun oneHourLater() = MoveRequest(
        eventId = 42L,
        beginMillis = beginMillis,
        endMillis = endMillis,
        target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis + 3_600_000L)),
        isAllDay = false,
        token = nextMoveToken(),
    )

    /** The end dragged an hour later, the start left where it is. */
    private fun endOneHourLater() = MoveRequest(
        eventId = 42L,
        beginMillis = beginMillis,
        endMillis = endMillis,
        target = MoveTarget.Span(
            Instant.fromEpochMilliseconds(beginMillis),
            Instant.fromEpochMilliseconds(endMillis + 3_600_000L),
        ),
        isAllDay = false,
        token = nextMoveToken(),
    )

    @Test
    fun `a one-off drop writes straight through with no scope prompt`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).hasSize(1)
        val (id, original, updated) = fake.updatedEvents.single()
        assertThat(id).isEqualTo(42L)
        assertThat(updated.start).isNotEqualTo(original.start)
        assertThat(vm.outcome.value).isInstanceOf(MoveOutcome.Moved::class.java)
    }

    @Test
    fun `a recurring drop parks for the scope instead of writing`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = false))
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `each scope maps to its own repository call`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val occurrence = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("a").also { it.toFile().mkdirs() }, occurrence).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.ThisEvent)
        }
        advanceUntilIdle()
        assertThat(occurrence.updatedOccurrences).hasSize(1)
        assertThat(occurrence.updatedOccurrences.single().second).isEqualTo(beginMillis)

        val following = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("b").also { it.toFile().mkdirs() }, following).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.ThisAndFollowing)
        }
        advanceUntilIdle()
        assertThat(following.updatedFromOccurrences).hasSize(1)

        val series = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("c").also { it.toFile().mkdirs() }, series).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.AllEvents)
        }
        advanceUntilIdle()
        assertThat(series.updatedEvents).hasSize(1)
    }

    @Test
    fun `a weekly BYDAY rule is realigned when the whole series moves weekday`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        // Without this the anchor becomes a Wednesday while the rule still says
        // Monday, and the series does not move at all.
        assertThat(fake.updatedEvents.single().third.rrule).isEqualTo("FREQ=WEEKLY;BYDAY=WE")
    }

    @Test
    fun `a rule that cannot be realigned offers only the single occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a same-time drop within one day still needs no realignment`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        // The weekday is unchanged, so the ambiguous BYDAY is not in the way.
        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = false))
    }

    @Test
    fun `a time-only drop that would carry the anchor past midnight offers only the occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // The anchor sits at 23:30 while the dragged occurrence is at midday —
        // only possible when the row pins no zone, so the two resolve an hour
        // apart. +1h leaves the occurrence on its day but rolls the anchor onto
        // the next one, which would leave BYDAY naming the wrong weekday.
        val anchor = LocalDateTime(LocalDate(2026, 6, 1), LocalTime(23, 30))
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO", anchorMillis = anchor) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a drop of another event waits for the one in flight, then lands`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail() }
        }
        val vm = slowViewModel(tempDir, fake)

        val first = oneHourLater()
        val second = toWednesday().copy(eventId = 7L, token = nextMoveToken())
        assertThat(vm.move(first)).isTrue()
        assertThat(vm.move(second)).isTrue()
        assertThat(vm.drops.value.pending).containsExactly(first.token, second.token)
        advanceUntilIdle()

        assertThat(fake.updatedEvents.map { it.first }).containsExactly(42L, 7L).inOrder()
        assertThat(vm.drops.value).isEqualTo(DropStates())
        assertThat(vm.inFlight.value).isFalse()
    }

    @Test
    fun `a failed drop lets go of its own copy only, not the one queued behind it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail() }
        }
        val vm = slowViewModel(tempDir, fake)

        // Zero distance: refused in prepare.
        val nowhere = oneHourLater().copy(
            target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis)),
            token = nextMoveToken(),
        )
        val queued = toWednesday().copy(eventId = 7L, token = nextMoveToken())
        vm.move(nowhere)
        vm.move(queued)
        advanceUntilIdle()

        assertThat(vm.drops.value.abandoned).containsExactly(nowhere.token)
        assertThat(fake.updatedEvents.map { it.first }).containsExactly(7L)
    }

    @Test
    fun `the same event dropped twice ends up where it was dropped last`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val hour = 3_600_000L
        val fake = FakeCalendarDataSource()
        val vm = slowViewModel(tempDir, fake)
        // Once the first drop is written, the event sits an hour later.
        fake.eventDetailResult = {
            if (fake.updatedEvents.isEmpty()) detail() else detail(anchorMillis = beginMillis + hour)
        }
        fake.instancesResult = { _, _ ->
            val shift = if (fake.updatedEvents.isEmpty()) 0L else hour
            listOf(
                testEvent(
                    id = 42L,
                    start = Instant.fromEpochMilliseconds(beginMillis + shift),
                    end = Instant.fromEpochMilliseconds(endMillis + shift),
                ),
            )
        }

        vm.move(oneHourLater())
        // Picked up from where the grid still drew it, and dropped two days on.
        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(fake.updatedEvents).hasSize(2)
        val last = fake.updatedEvents.last().third
        assertThat(last.start.dateTime).isEqualTo(LocalDateTime(LocalDate(2026, 6, 10), LocalTime(13, 0)))
    }

    @Test
    fun `a drop on a series written moments ago is not written`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // A whole-series write shifts every occurrence; the grid still draws the
        // old ones, and another occurrence may now sit at the dragged time.
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail(rrule = "FREQ=DAILY") } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()
        vm.consumeOutcome()

        val again = toWednesday()
        vm.move(again)
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Stale)
        assertThat(vm.drops.value.abandoned).containsExactly(again.token)
        assertThat(fake.updatedEvents).hasSize(1)
    }

    @Test
    fun `a single occurrence written leaves the rest of its series movable`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail(rrule = "FREQ=WEEKLY") } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        vm.moveWithScope(RecurringWriteScope.ThisEvent)
        advanceUntilIdle()

        // Another occurrence, a week on.
        val week = 7 * 86_400_000L
        vm.move(oneHourLater().copy(beginMillis = beginMillis + week, endMillis = endMillis + week, token = nextMoveToken()))
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNotNull()
    }

    @Test
    fun `chip messages wait their turn instead of replacing each other`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { id ->
                if (id == 42L) detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE", status = EventStatus.Cancelled) else detail()
            }
        }
        val vm = slowViewModel(tempDir, fake)

        vm.move(toWednesday())
        vm.move(toWednesday().copy(eventId = 7L, token = nextMoveToken()))
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.BlockedCancelledSeries)
        vm.consumeOutcome()
        assertThat((vm.outcome.value as MoveOutcome.Moved).undo?.eventId).isEqualTo(7L)
        vm.consumeOutcome()
        assertThat(vm.outcome.value).isNull()
    }

    @Test
    fun `a full queue says so`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = slowViewModel(tempDir, fake)

        repeat(9) { i -> vm.move(toWednesday().copy(eventId = 100L + i, token = nextMoveToken())) }
        val refused = toWednesday().copy(eventId = 200L, token = nextMoveToken())
        assertThat(vm.move(refused)).isFalse()

        assertThat(vm.drops.value.abandoned).containsExactly(refused.token)
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.QueueFull)
    }

    @Test
    fun `a deleted event reports itself as gone, not moved`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { null } }
        val vm = viewModel(tempDir, fake, present = emptyList())

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Gone)
    }

    @Test
    fun `drops waiting when a scope dialog opens are cancelled, not held behind it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { id -> if (id == 42L) detail(rrule = "FREQ=WEEKLY") else detail() }
        }
        val vm = slowViewModel(tempDir, fake)

        vm.move(oneHourLater())
        val queued = toWednesday().copy(eventId = 7L, token = nextMoveToken())
        vm.move(queued)
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNotNull()
        assertThat(vm.drops.value.abandoned).containsExactly(queued.token)
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Skipped)

        // One made while the dialog is up is refused the same way.
        val during = toWednesday().copy(eventId = 7L, token = nextMoveToken())
        assertThat(vm.move(during)).isFalse()

        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()
        assertThat(fake.updatedEvents.map { it.first }).containsExactly(42L)
        // The skip has its turn, then the move it waited on, with its Undo.
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Skipped)
        vm.consumeOutcome()
        assertThat((vm.outcome.value as MoveOutcome.Moved).undo?.eventId).isEqualTo(42L)
    }

    @Test
    fun `the first move of a run keeps the chip and its undo`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = slowViewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.move(toWednesday().copy(eventId = 7L, token = nextMoveToken()))
        advanceUntilIdle()

        val shown = vm.outcome.value as MoveOutcome.Moved
        assertThat(fake.updatedEvents).hasSize(2)
        assertThat(shown.undo?.eventId).isEqualTo(42L)
    }

    @Test
    fun `a later move of the same event takes the first move's undo away`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = slowViewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(fake.updatedEvents).hasSize(2)
        assertThat((vm.outcome.value as MoveOutcome.Moved).undo).isNull()
    }

    @Test
    fun `undo is refused while a write is running`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo!!

        // A second drop, of another event, parks on its scope dialog, which holds the pipeline.
        fake.eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        vm.move(toWednesday().copy(eventId = 7L, token = nextMoveToken()))
        advanceUntilIdle()

        val abandoned = vm.abandonedCount
        assertThat(vm.undo(undo)).isFalse()
        advanceUntilIdle()
        assertThat(fake.updatedEvents).hasSize(1)
        // Nothing ticks: the refusal started no journey of its own, and the
        // counter is global — a tick here would release the copy the drop that
        // *is* in flight is holding, before the grid has drawn it.
        assertThat(vm.abandonedCount).isEqualTo(abandoned)
    }

    @Test
    fun `an undo that fails lets the held chip go`(@TempDir tempDir: Path) = runTest(dispatcher) {
        // undoStarted has already sent the chip back on its journey by the time
        // the inverse write is attempted. Without a tick here nothing ever seats
        // it, and the overlay waits out its whole settle timeout for a chip that
        // is never coming.
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo!!

        val abandoned = vm.abandonedCount
        fake.writeError = SecurityException("permission revoked")
        assertThat(vm.undo(undo)).isTrue()
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.WriteDenied)
        assertThat(vm.abandonedCount).isEqualTo(abandoned + 1)
    }

    @Test
    fun `an undo that lands leaves the abandoned count alone`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo!!

        val abandoned = vm.abandonedCount
        vm.undo(undo)
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Undone)
        assertThat(vm.abandonedCount).isEqualTo(abandoned)
    }

    @Test
    fun `an exception row is written as a plain event, never as a nested exception`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            // A sync adapter that left the series' rule on the override row.
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY", isException = true) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).hasSize(1)
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `a drop past the series' own UNTIL is refused, not written`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            // Midday UTC, so the device zone can't push the UNTIL date onto the
            // day the event is being dragged to.
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;UNTIL=20260608T120000Z") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.BlockedSeriesEnd)
        assertThat(fake.updatedEvents).isEmpty()
    }

    @Test
    fun `dragging a bounded series' last occurrence still allows moving just it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;UNTIL=20260608T120000Z") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        advanceUntilIdle()
        // An exception row is constrained by no UNTIL, so the scope stays on offer.
        assertThat(vm.scopePrompt.value).isNotNull()

        vm.moveWithScope(RecurringWriteScope.ThisEvent)
        advanceUntilIdle()

        assertThat(fake.updatedOccurrences).hasSize(1)
        assertThat(vm.outcome.value).isInstanceOf(MoveOutcome.Moved::class.java)
    }

    @Test
    fun `a drag that changes both the day and the time cannot move the series`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        // Onto the next day *and* two hours later: a late enough anchor would
        // cross two midnights under the same wall-clock shift.
        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Start(
                    Instant.fromEpochMilliseconds(beginMillis + 26 * 3_600_000L),
                ),
                isAllDay = false,
                token = nextMoveToken(),
            ),
        )
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a zero-distance drop writes nothing and says nothing`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis)),
                isAllDay = false,
                token = nextMoveToken(),
            ),
        )
        advanceUntilIdle()

        assertThat(fake.updatedEvents).isEmpty()
        assertThat(vm.outcome.value).isNull()
    }

    @Test
    fun `a vanished event reports itself as gone`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { null } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Gone)
    }

    @Test
    fun `a revoked write permission is reported apart from a plain failure`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail() }
            writeError = SecurityException("revoked")
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.WriteDenied)
    }

    @Test
    fun `undo writes the move back the other way`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo
        assertThat(undo).isNotNull()

        vm.undo(undo!!)
        advanceUntilIdle()

        assertThat(fake.updatedEvents).hasSize(2)
        val forward = fake.updatedEvents[0]
        val back = fake.updatedEvents[1]
        assertThat(back.second).isEqualTo(forward.third)
        assertThat(back.third).isEqualTo(forward.second)
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Undone)
    }

    @Test
    fun `an occurrence write offers no undo, since shifting back cannot restore it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.moveWithScope(RecurringWriteScope.ThisEvent)
        advanceUntilIdle()

        assertThat((vm.outcome.value as MoveOutcome.Moved).undo).isNull()
    }

    @Test
    fun `only writable, unmanaged calendars can be dragged from`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(
                cal(1L),
                cal(2L, canModify = false),
                cal(3L, managed = true),
            )
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)
        advanceUntilIdle()

        assertThat(vm.movableCalendarIds.value).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `cancelling the scope dialog writes nothing`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.cancelScope()
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `a drop that lands nothing says so, since move() already answered yes`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        // Refused in prepare: nowhere to move to.
        assertThat(vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis)),
                isAllDay = false,
                token = nextMoveToken(),
            ),
        )).isTrue()
        advanceUntilIdle()
        assertThat(vm.abandonedCount).isEqualTo(1)

        // Refused by the provider, after the write was attempted.
        fake.writeError = SecurityException("revoked")
        vm.move(oneHourLater())
        advanceUntilIdle()
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.WriteDenied)
        assertThat(vm.abandonedCount).isEqualTo(2)
    }

    @Test
    fun `a scope dialog dismissed leaves nothing waiting on the write`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()
        assertThat(vm.abandonedCount).isEqualTo(0)

        vm.cancelScope()
        advanceUntilIdle()
        assertThat(vm.abandonedCount).isEqualTo(1)
    }

    @Test
    fun `a written move never counts as abandoned`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isInstanceOf(MoveOutcome.Moved::class.java)
        assertThat(vm.abandonedCount).isEqualTo(0)
    }

    @Test
    fun `a resize writes the new length and reports both ends`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(endOneHourLater())
        advanceUntilIdle()

        val (_, original, updated) = fake.updatedEvents.single()
        assertThat(updated.start).isEqualTo(original.start)
        assertThat(updated.end).isNotEqualTo(original.end)
        val moved = vm.outcome.value as MoveOutcome.Moved
        assertThat(moved.startMillis).isEqualTo(beginMillis)
        assertThat(moved.endMillis).isEqualTo(endMillis + 3_600_000L)
        assertThat(moved.undo).isNotNull()
    }

    @Test
    fun `a moved event reports no end, so its chip keeps the move wording`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat((vm.outcome.value as MoveOutcome.Moved).endMillis).isNull()
    }

    @Test
    fun `resizing a recurring event asks for the scope, as a resize`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(endOneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value)
            .isEqualTo(MoveScopePrompt(occurrenceOnly = false, resize = true))
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()
        // The start stays put, so the rule needs no realigning.
        assertThat(fake.updatedEvents.single().third.rrule).isEqualTo("FREQ=WEEKLY;BYDAY=MO")
    }

    @Test
    fun `pulling a recurring event's start back over midnight resizes only the occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)
        val sundayEvening = LocalDateTime(LocalDate(2026, 6, 7), LocalTime(22, 0))
            .toInstant(TimeZone.currentSystemDefault())

        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Span(sundayEvening, Instant.fromEpochMilliseconds(endMillis)),
                isAllDay = false,
                token = nextMoveToken(),
            ),
        )
        advanceUntilIdle()

        // Not a whole-day shift, so the series anchor can't follow.
        assertThat(vm.scopePrompt.value)
            .isEqualTo(MoveScopePrompt(occurrenceOnly = true, resize = true))
    }

    @Test
    fun `a cancelled series moves as a whole without asking`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY", status = EventStatus.Cancelled) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).hasSize(1)
        assertThat(fake.updatedOccurrences).isEmpty()
        assertThat(fake.updatedFromOccurrences).isEmpty()
    }

    @Test
    fun `a cancelled series whose rule cannot be realigned is not moved at all`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE", status = EventStatus.Cancelled) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.BlockedCancelledSeries)
        assertThat(vm.abandonedCount).isEqualTo(1)
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `an all-day event dropped into the time grid becomes timed`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val dayStart = monday.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = {
                testDetail(
                    instance = testEvent(
                        id = 42L, title = "Offsite", isAllDay = true,
                        start = Instant.fromEpochMilliseconds(dayStart),
                        end = Instant.fromEpochMilliseconds(dayStart + 86_400_000L),
                    ),
                )
            }
        }
        val vm = viewModel(tempDir, fake)
        val start = Instant.fromEpochMilliseconds(beginMillis)

        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = dayStart,
                endMillis = dayStart + 86_400_000L,
                target = MoveTarget.Span(start, Instant.fromEpochMilliseconds(endMillis)),
                isAllDay = true,
                token = nextMoveToken(),
            ),
        )
        advanceUntilIdle()

        val updated = fake.updatedEvents.single().third
        assertThat(updated.isAllDay).isFalse()
        assertThat(updated.start.dateTime).isEqualTo(LocalDateTime(monday, LocalTime(12, 0)))
        assertThat(updated.end.dateTime).isEqualTo(LocalDateTime(monday, LocalTime(13, 0)))
    }

    @Test
    fun `a recurring all-day event dropped into the grid early on its own day can still move the series`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val dayStart = monday.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = {
                testDetail(
                    instance = testEvent(
                        id = 42L, title = "Offsite", isAllDay = true,
                        start = Instant.fromEpochMilliseconds(dayStart),
                        end = Instant.fromEpochMilliseconds(dayStart + 86_400_000L),
                    ),
                    rrule = "FREQ=WEEKLY;BYDAY=MO",
                )
            }
        }
        val vm = viewModel(tempDir, fake)
        val eight = LocalDateTime(monday, LocalTime(8, 0)).toInstant(TimeZone.currentSystemDefault())

        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = dayStart,
                endMillis = dayStart + 86_400_000L,
                target = MoveTarget.Span(eight, eight + 1.hours),
                isAllDay = true,
                token = nextMoveToken(),
            ),
        )
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = false))
    }
}
