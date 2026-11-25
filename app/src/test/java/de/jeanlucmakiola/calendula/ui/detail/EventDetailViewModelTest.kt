package de.jeanlucmakiola.calendula.ui.detail

import android.content.ContextWrapper
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.MainDispatcherExtension
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.time.FakeZoneSource
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.testDetail
import de.jeanlucmakiola.calendula.testEvent
import de.jeanlucmakiola.calendula.testPrefs
import de.jeanlucmakiola.calendula.testRepository
import de.jeanlucmakiola.calendula.testSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Re-opening an occurrence must re-read it (#196): the view model outlives the
 * sheet, so an edit that changed no time would otherwise show the pre-save row.
 * The re-read stays silent — the loaded content must not blink back to the
 * skeleton on the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    @JvmField @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()
    private val dispatcher = mainDispatcher.dispatcher

    private val beginMillis = 1_781_164_800_000L
    private val endMillis = beginMillis + 3_600_000L

    private fun detail(description: String?) = testDetail(
        instance = testEvent(
            id = 42L, title = "Standup",
            start = Instant.fromEpochMilliseconds(beginMillis),
            end = Instant.fromEpochMilliseconds(endMillis),
            color = 0xFF000000.toInt(),
        ),
        description = description,
    )

    private fun viewModel(tempDir: Path, fake: FakeCalendarDataSource): EventDetailViewModel {
        val repo = testRepository(
            fake, tempDir, dispatcher,
            prefs = testPrefs(tempDir, CoroutineScope(dispatcher)),
            settings = testSettings(tempDir, CoroutineScope(dispatcher)),
        )
        // Only `shareUri()` touches the exporter, and nothing here shares.
        return EventDetailViewModel(repo, IcsExporter(ContextWrapper(null)), dispatcher, FakeZoneSource())
    }

    private fun fakeSource(description: () -> String?) = FakeCalendarDataSource().apply {
        calendarsResult = listOf(
            CalendarSource(
                id = 1L, displayName = "Cal", accountName = "acc@local", accountType = "LOCAL",
                color = 0xFF112233.toInt(), isVisibleInSystem = true, canModifyContents = true,
            ),
        )
        eventDetailResult = { detail(description()) }
    }

    @Test
    fun `re-opening the same occurrence re-reads it`(@TempDir tempDir: Path) = runTest(dispatcher) {
        var stored: String? = null
        val vm = viewModel(tempDir, fakeSource { stored })
        val collector = launch(Job()) { vm.state.collect {} }

        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat((vm.state.value as EventDetailUiState.Success).detail.description).isNull()

        // The edit screen saved a description; the tapped occurrence is unchanged.
        stored = "Bring the roadmap"
        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat((vm.state.value as EventDetailUiState.Success).detail.description)
            .isEqualTo("Bring the roadmap")

        collector.cancel()
    }

    @Test
    fun `the re-read does not fall back to the skeleton`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir, fakeSource { null })
        val seen = mutableListOf<EventDetailUiState>()
        val collector = launch(Job()) { vm.state.collect { seen += it } }

        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(EventDetailUiState.Success::class.java)

        seen.clear()
        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat(seen).doesNotContain(EventDetailUiState.Loading)

        collector.cancel()
    }
}
