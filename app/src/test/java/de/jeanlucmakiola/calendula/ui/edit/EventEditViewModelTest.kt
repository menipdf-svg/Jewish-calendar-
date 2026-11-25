package de.jeanlucmakiola.calendula.ui.edit

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.MainDispatcherExtension
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.time.FakeZoneSource
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.FormTime
import de.jeanlucmakiola.calendula.domain.startInstant
import de.jeanlucmakiola.calendula.domain.problems
import de.jeanlucmakiola.calendula.domain.endInstant
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
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Focuses on the save-time branch that decides between an in-place update and a
 * calendar *move* (copy+delete). The provider-level move itself is verified
 * on-device; here the fake records which repository call the ViewModel chose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventEditViewModelTest {

    @JvmField @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()
    private val dispatcher = mainDispatcher.dispatcher

    private val beginMillis = 1_781_164_800_000L
    private val endMillis = beginMillis + 3_600_000L

    private fun cal(
        id: Long,
        visible: Boolean = true,
        syncsEvents: Boolean = true,
    ): CalendarSource = CalendarSource(
        id = id, displayName = "Cal $id", accountName = "acc@local", accountType = "LOCAL",
        color = 0xFF112233.toInt(), isVisibleInSystem = visible, canModifyContents = true,
        syncsEvents = syncsEvents,
    )

    private fun detail(
        calendarId: Long,
        rrule: String? = null,
        start: Instant = Instant.fromEpochMilliseconds(beginMillis),
        end: Instant = Instant.fromEpochMilliseconds(endMillis),
    ): EventDetail = testDetail(
        instance = testEvent(
            id = 42L, calendarId = calendarId, title = "Standup",
            start = start,
            end = end,
            color = 0xFF000000.toInt(),
        ),
        rrule = rrule,
    )

    // Pin the DataStore's scope to the test dispatcher so a settings write (e.g.
    // last-used calendar) completes under advanceUntilIdle instead of on a real
    // IO thread the virtual clock can't observe.
    private fun prefs(tempDir: Path): CalendarPrefs = testPrefs(tempDir, CoroutineScope(dispatcher))

    private fun settings(tempDir: Path): SettingsPrefs = testSettings(tempDir, CoroutineScope(dispatcher))

    private fun viewModel(
        tempDir: Path,
        fake: FakeCalendarDataSource,
        // Passed in by the tests that need to seed a setting first; a second
        // DataStore on the same file would clash, so it is built only once.
        s: SettingsPrefs = settings(tempDir),
        zones: FakeZoneSource = FakeZoneSource(),
        // A queued dispatcher here holds provider reads and writes until the test
        // advances, so it can close the form while one is in flight.
        io: CoroutineDispatcher = dispatcher,
    ): EventEditViewModel {
        val p = prefs(tempDir)
        val repo = testRepository(fake, tempDir, io, p, s)
        // Only `shareUri()` touches the exporter, and nothing here shares.
        return EventEditViewModel(repo, p, s, IcsExporter(ContextWrapper(null)), dispatcher, zones)
    }

    /** Keep [EventEditViewModel.state] hot so it computes while the test drives it. */
    private fun CoroutineScope.activate(vm: EventEditViewModel): Job = launch { vm.state.collect {} }

    @Test
    fun `a calendar switched off in settings is not offered as a target`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, visible = false))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `a calendar whose account is not synced to this device is not a target`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // Writable and switched on, but the account keeps its events off the
        // device: nothing saved here ever reaches it, and the provider drops the
        // rows when the subscription comes back (#76).
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, syncsEvents = false))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `editing an event in a switched-off calendar keeps it in the picker`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // Otherwise the calendar row renders as the "no calendar" error and any
        // pick routes the save through a move the user never asked for.
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, visible = false))
            eventDetailResult = { detail(calendarId = 2L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L, 2L)
        assertThat(vm.state.value?.form?.calendarId).isEqualTo(2L)
        job.cancel()
    }

    @Test
    fun `changing the calendar routes the save through a move, not an update`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L) }
            nextInsertId = 99L
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setCalendar(2L)
        vm.save()
        advanceUntilIdle()

        assertThat(fake.movedEvents).hasSize(1)
        assertThat(fake.movedEvents.single().eventId).isEqualTo(42L)
        assertThat(fake.movedEvents.single().targetCalendarId).isEqualTo(2L)
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Saved)
        job.cancel()
    }

    @Test
    fun `moving a recurring event skips the scope dialog`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L, rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setCalendar(2L)
        vm.save()
        advanceUntilIdle()

        // No AwaitingScope park: a move is inherently whole-series.
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Saved)
        assertThat(fake.movedEvents).hasSize(1)
        job.cancel()
    }

    @Test
    fun `a new event takes its calendar's default duration, and follows a switch`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L), cal(2L)) }
        val s = settings(tempDir)
        s.setDefaultEventDurationMinutes(30)
        s.setCalendarEventDuration(2L, 480)
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15), startMinutes = 9 * 60)
        advanceUntilIdle()

        // Calendar 1 has no length of its own, so it inherits the global 30 min.
        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 9, 30))

        vm.setCalendar(2L)
        advanceUntilIdle()

        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 17, 0))
        job.cancel()
    }

    @Test
    fun `an end time set by hand survives a calendar switch`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L), cal(2L)) }
        val s = settings(tempDir)
        s.setCalendarEventDuration(2L, 480)
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15), startMinutes = 9 * 60)
        advanceUntilIdle()
        vm.setEndTime(LocalTime(10, 15))
        vm.setCalendar(2L)
        advanceUntilIdle()

        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 10, 15))
        job.cancel()
    }

    @Test
    fun `an imported event keeps the span its source named`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L), cal(2L)) }
        val s = settings(tempDir)
        s.setDefaultEventDurationMinutes(30)
        s.setCalendarEventDuration(2L, 480)
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        vm.openImported(importedForm(end = LocalDateTime(2030, 1, 15, 11, 30)), ImportSource.File)
        advanceUntilIdle()
        vm.setCalendar(2L)
        advanceUntilIdle()

        // The file owns the length; a calendar switch must not restretch it.
        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 11, 30))
        job.cancel()
    }

    @Test
    fun `an insert intent with no end takes the default duration`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L), cal(2L)) }
        val s = settings(tempDir)
        s.setDefaultEventDurationMinutes(30)
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        // The placeholder hour buildInsertEventForm fills in for a missing end.
        vm.openImported(
            importedForm(end = LocalDateTime(2030, 1, 15, 10, 0)),
            ImportSource.InsertOpenEnded,
        )
        advanceUntilIdle()

        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 9, 30))
        job.cancel()
    }

    private fun importedForm(end: LocalDateTime) = EventForm(
        calendarId = null,
        title = "Imported",
        start = FormTime(LocalDateTime(2030, 1, 15, 9, 0)),
        end = FormTime(end),
    )

    @Test
    fun `an all-day event ignores the default duration`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L), cal(2L)) }
        val s = settings(tempDir)
        s.setCalendarEventDuration(2L, 480)
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15), startMinutes = 9 * 60)
        advanceUntilIdle()
        vm.setAllDay(true)
        vm.setCalendar(2L)
        advanceUntilIdle()

        // Dates carry no length: the times stay where the form put them.
        assertThat(vm.state.value?.form?.end?.dateTime).isEqualTo(LocalDateTime(2030, 1, 15, 10, 0))
        job.cancel()
    }

    @Test
    fun `editing a recurring event without moving still asks for the scope`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L, rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setTitle("Renamed")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.AwaitingScope)
        assertThat(fake.movedEvents).isEmpty()
        job.cancel()
    }

    @Test
    fun `setTitle caps a long paste at 255 chars`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15))
        advanceUntilIdle()
        vm.setTitle("a".repeat(300))

        assertThat(vm.state.value?.form?.title).hasLength(MAX_SINGLE_LINE_FIELD)
        job.cancel()
    }

    @Test
    fun `setTitle refuses to grow past 255 chars once capped`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15))
        advanceUntilIdle()
        vm.setTitle("a".repeat(255))
        vm.setTitle("a".repeat(256))

        assertThat(vm.state.value?.form?.title).hasLength(255)
        job.cancel()
    }

    @Test
    fun `setLocation joins a multi-line address then caps it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openNew(LocalDate(2030, 1, 15))
        advanceUntilIdle()
        vm.setLocation("${"a".repeat(200)}\n${"b".repeat(200)}")

        val location = vm.state.value?.form?.location
        assertThat(location).hasLength(MAX_SINGLE_LINE_FIELD)
        assertThat(location).contains(", ")
        job.cancel()
    }

    @Test
    fun `capLength lets an already-over-long value shrink`() {
        val overLong = "a".repeat(300)
        val shortened = overLong.dropLast(1)

        assertThat(capLength(shortened, overLong)).hasLength(299)
    }

    @Test
    fun `capLength never splits a surrogate pair at the cut point`() {
        // U+1F600, a surrogate pair, sits right across the 255 boundary.
        val new = "a".repeat(254) + "😀"

        assertThat(capLength(new, "")).isEqualTo("a".repeat(254))
    }

    @Test
    fun `an edit counts as unsaved only while it differs from the loaded event`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isFalse()

        vm.setTitle("Retro")
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isTrue()

        vm.setTitle("Standup")
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isFalse()
        job.cancel()
    }

    @Test
    fun `a new form holding only its defaults has nothing to lose`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openNew(LocalDate(2026, 10, 12), startMinutes = 9 * 60)
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isFalse()

        vm.setTitle("Lunch")
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isTrue()

        vm.setTitle("")
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isFalse()
        job.cancel()
    }

    @Test
    fun `deleting from the form removes the event and reports it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()
        vm.delete(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        assertThat(fake.deletedEventIds).containsExactly(42L)
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Deleted)
        job.cancel()
    }

    @Test
    fun `deleting an event that is already gone counts as deleted`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()
        fake.writeError = NoSuchEventException(42L)
        vm.delete(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Deleted)
        job.cancel()
    }

    @Test
    fun `a delete without write access reports it as a delete`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()
        fake.writeError = SecurityException()
        vm.delete(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.DeleteDenied)
        job.cancel()
    }

    @Test
    fun `a blank rrule does not make the edited event recurring`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L, rrule = "") }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.editedEventRecurs()).isFalse()
        job.cancel()
    }

    @Test
    fun `accepting the default reminders for an import counts as a change`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val s = settings(tempDir)
        s.setDefaultReminderMinutes(listOf(15))
        val vm = viewModel(tempDir, fake, s)
        val job = activate(vm)

        vm.openImported(importedForm(end = LocalDateTime(2030, 1, 15, 10, 0)), ImportSource.File)
        advanceUntilIdle()
        assertThat(vm.importReminderPrompt.value).isNotNull()
        assertThat(vm.hasUnsavedChanges.value).isFalse()

        vm.applyImportedReminderDefault()
        advanceUntilIdle()
        assertThat(vm.hasUnsavedChanges.value).isTrue()
        job.cancel()
    }

    @Test
    fun `a drafted block opens at its own instants across the fall-back hour`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val vm = viewModel(tempDir, fake, zones = FakeZoneSource(berlin))
        val job = activate(vm)

        // An hour from 02:30 CEST ends at 02:30 CET, the same wall clock.
        vm.openDraft(Instant.parse("2026-10-25T00:30:00Z"), Instant.parse("2026-10-25T01:30:00Z"))
        advanceUntilIdle()

        val form = vm.state.value!!.form!!
        assertThat(form.startInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T00:30:00Z"))
        assertThat(form.endInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
        assertThat(form.problems(berlin)).isEmpty()
        job.cancel()
    }

    @Test
    fun `a drafted block on the second pass stays on it at its length`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(cal(1L)) }
        val s = settings(tempDir)
        s.setCalendarEventDuration(1L, 480)
        val vm = viewModel(tempDir, fake, s, FakeZoneSource(berlin))
        val job = activate(vm)

        // 02:30 CET to 03:00 CET: the default length mustn't stretch it.
        vm.openDraft(Instant.parse("2026-10-25T01:30:00Z"), Instant.parse("2026-10-25T02:00:00Z"))
        advanceUntilIdle()

        val form = vm.state.value!!.form!!
        assertThat(form.startInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T01:30:00Z"))
        assertThat(form.endInstant(berlin)).isEqualTo(Instant.parse("2026-10-25T02:00:00Z"))
        job.cancel()
    }

    @Test
    fun `a time picked in the repeated hour and back keeps the opened pass`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // Opened at 02:30 CET (the second pass) to 03:30, its start moved away and back.
        val begin = Instant.parse("2026-10-25T01:30:00Z")
        val end = Instant.parse("2026-10-25T02:30:00Z")
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L, start = begin, end = end) }
        }
        val vm = viewModel(tempDir, fake, zones = FakeZoneSource(berlin))
        val job = activate(vm)
        vm.openForEdit(eventId = 1L, beginMillis = begin.toEpochMilliseconds(), endMillis = end.toEpochMilliseconds())
        advanceUntilIdle()
        val opened = vm.state.value!!.form!!

        vm.setStartTime(LocalTime(4, 0))
        vm.setStartTime(LocalTime(2, 30))

        assertThat(vm.state.value!!.form).isEqualTo(opened)
        job.cancel()
    }

    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `all-day on and off keeps a second-pass event where it was`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val begin = Instant.parse("2026-10-25T01:30:00Z")
        val end = Instant.parse("2026-10-25T02:30:00Z")
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L, start = begin, end = end) }
        }
        val vm = viewModel(tempDir, fake, zones = FakeZoneSource(berlin))
        val job = activate(vm)
        vm.openForEdit(eventId = 1L, beginMillis = begin.toEpochMilliseconds(), endMillis = end.toEpochMilliseconds())
        advanceUntilIdle()
        val opened = vm.state.value!!.form!!

        vm.setAllDay(true)
        vm.setAllDay(false)
        advanceUntilIdle()

        assertThat(vm.state.value!!.form).isEqualTo(opened)
        job.cancel()
    }

    @Test
    fun `a load abandoned by closing the form never lands on the next one`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { id ->
                val base = detail(calendarId = 1L)
                base.copy(instance = base.instance.copy(eventId = id, title = "Event $id"))
            }
        }
        val vm = viewModel(tempDir, fake, io = StandardTestDispatcher(testScheduler))
        val job = activate(vm)

        vm.openForEdit(eventId = 1L, beginMillis = beginMillis, endMillis = endMillis)
        vm.reset()
        advanceUntilIdle()
        vm.openForEdit(eventId = 2L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.form?.title).isEqualTo("Event 2")
        job.cancel()
    }

    @Test
    fun `a failed load abandoned by closing the form doesn't close the next one`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { null }
        }
        val vm = viewModel(tempDir, fake, io = StandardTestDispatcher(testScheduler))
        val job = activate(vm)

        vm.openForEdit(eventId = 1L, beginMillis = beginMillis, endMillis = endMillis)
        vm.reset()
        advanceUntilIdle()
        vm.openNew(LocalDate(2026, 10, 12), startMinutes = 9 * 60)
        advanceUntilIdle()

        assertThat(vm.loadFailed.value).isFalse()
        job.cancel()
    }

    @Test
    fun `a delete still running when the form closes doesn't report to the next one`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake, io = StandardTestDispatcher(testScheduler))
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()
        vm.delete(RecurringWriteScope.AllEvents)
        vm.reset()
        vm.openNew(LocalDate(2026, 10, 12), startMinutes = 9 * 60)
        advanceUntilIdle()

        assertThat(fake.deletedEventIds).containsExactly(42L)
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Idle)
        job.cancel()
    }
}
