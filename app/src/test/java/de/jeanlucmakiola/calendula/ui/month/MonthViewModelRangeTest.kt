package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.MainDispatcherExtension
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.time.TodaySource
import de.jeanlucmakiola.calendula.testPrefs
import de.jeanlucmakiola.calendula.testRepository
import de.jeanlucmakiola.calendula.testSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The month view never leaves the span its pager can show (#457). */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModelRangeTest {

    @JvmField @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()
    private val dispatcher = mainDispatcher.dispatcher

    private fun viewModel(tempDir: Path): MonthViewModel {
        val settings = testSettings(tempDir, CoroutineScope(dispatcher))
        val repo = testRepository(
            FakeCalendarDataSource(), tempDir, dispatcher,
            prefs = testPrefs(tempDir, CoroutineScope(dispatcher)),
            settings = settings,
        )
        val today = object : TodaySource {
            override val today = flowOf(LocalDate(2026, 11, 2))
        }
        return MonthViewModel(repo, settings, today, dispatcher)
    }

    @Test
    fun `a focus before 1600 opens the first month`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir)
        vm.goToDate(LocalDate(1599, 12, 31))
        assertThat(vm.month.value).isEqualTo(YearMonth(1600, Month.JANUARY))
        assertThat(vm.selectedDate.value).isEqualTo(LocalDate(1600, 1, 1))
    }

    @Test
    fun `a focus after 2399 opens the last month`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir)
        vm.goToDate(LocalDate(2400, 1, 1))
        assertThat(vm.month.value).isEqualTo(YearMonth(2399, Month.DECEMBER))
    }

    @Test
    fun `a tap on a day past the span is ignored`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir)
        vm.goToDate(LocalDate(1600, 1, 1))
        vm.selectDate(LocalDate(1599, 12, 31))
        assertThat(vm.month.value).isEqualTo(YearMonth(1600, Month.JANUARY))
        assertThat(vm.selectedDate.value).isEqualTo(LocalDate(1600, 1, 1))
    }

    @Test
    fun `the split pane stops at the first month`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir)
        vm.goToDate(LocalDate(1600, 1, 15))
        vm.clearSelection()
        vm.stepSplitPane(-1)
        assertThat(vm.month.value).isEqualTo(YearMonth(1600, Month.JANUARY))
    }
}
