package com.menipdf.jewishcalendar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/** צילומי מסך ובדיקות התנהגות של הלוח ברוחבים שונים (Robolectric, גרפיקה מקורית). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CalendarUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun newVm(date: LocalDate, view: ViewMode = ViewMode.MONTH, dark: Boolean = false, daf: Boolean = false): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        vm.cursor = date
        vm.view = view
        vm.theme = if (dark) "dark" else "light"
        vm.filters[Cat.DAF] = daf
        return vm
    }

    private fun show(vm: AppViewModel, fontScale: Float = 1f) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                CalendarApp(vm)
            }
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").also { it.mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /* ---------- צילומי מסך ---------- */
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun month_few_events_360() { show(newVm(LocalDate.of(2026, 11, 15))); shot("01_month_nov_360") }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun month_many_holidays_360() { show(newVm(LocalDate.of(2026, 9, 15))); shot("02_month_sep_360") }

    @Test @Config(qualifiers = "w384dp-h800dp-xxhdpi")
    fun month_many_holidays_384() { show(newVm(LocalDate.of(2026, 9, 15))); shot("03_month_sep_384") }

    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun month_many_holidays_393() { show(newVm(LocalDate.of(2026, 9, 15))); shot("04_month_sep_393") }

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun month_sukkot_412() { show(newVm(LocalDate.of(2026, 10, 10))); shot("05_month_oct_412") }

    @Test @Config(qualifiers = "w430dp-h932dp-xxhdpi")
    fun month_many_holidays_430() { show(newVm(LocalDate.of(2026, 9, 15))); shot("06_month_sep_430") }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun month_dark_360() { show(newVm(LocalDate.of(2026, 9, 15), dark = true)); shot("07_month_sep_dark_360") }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun month_overflow_daf_360() { show(newVm(LocalDate.of(2026, 12, 15), daf = true)); shot("08_month_dec_daf_360") }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun month_font_scale_1_5_360() { show(newVm(LocalDate.of(2026, 9, 15)), fontScale = 1.5f); shot("09_month_sep_fontscale_360") }

    @Test @Config(qualifiers = "w820dp-h1180dp-xhdpi")
    fun month_tablet() { show(newVm(LocalDate.of(2026, 10, 10))); shot("10_month_oct_tablet") }

    @Test @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun month_large_screen() { show(newVm(LocalDate.of(2026, 10, 10))); shot("11_month_oct_large") }

    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun week_nov_393() { show(newVm(LocalDate.of(2026, 11, 25), ViewMode.WEEK)); shot("12_week_nov_393") }

    @Test @Config(qualifiers = "w393dp-h852dp-xxhdpi")
    fun day_nov_393() { show(newVm(LocalDate.of(2026, 11, 27), ViewMode.DAY)); shot("13_day_fri_393") }

    @Test @Config(qualifiers = "w384dp-h800dp-xxhdpi")
    fun agenda_384() { show(newVm(LocalDate.of(2026, 9, 8), ViewMode.AGENDA)); shot("14_agenda_sep_384") }

    /* ---------- התנהגות ---------- */
    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun navigation_nextPrev_acrossYear() {
        val vm = newVm(LocalDate.of(2026, 12, 15))
        show(vm)
        rule.onNodeWithText("דצמבר 2026").assertExists()
        rule.onNodeWithContentDescription("הבא").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("ינואר 2027").assertExists()
        rule.onNodeWithContentDescription("הקודם").performClick()
        rule.onNodeWithContentDescription("הקודם").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("נובמבר 2026").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun todayButton_goesToCurrentMonth() {
        val vm = newVm(LocalDate.of(2020, 3, 10))
        show(vm)
        rule.onNodeWithText("היום").performClick()
        rule.waitForIdle()
        assertEquals(LocalDate.now(), vm.cursor)
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun tapDay_opensDetailsSheet() {
        show(newVm(LocalDate.of(2026, 11, 15)))
        rule.onNode(hasContentDescription("27 בנובמבר", substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("פתח תצוגת יום").assertExists()
        rule.onNodeWithText("כניסת שבת").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun overflowIndicator_isShown_andOpensSheet() {
        show(newVm(LocalDate.of(2026, 12, 15), daf = true))
        rule.onNode(hasContentDescription("10 בדצמבר", substring = true) and hasClickAction()).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("פתח תצוגת יום").assertExists()
    }

    @Test @Config(qualifiers = "w360dp-h740dp-xxhdpi")
    fun weekdaysAllVisible_360() {
        show(newVm(LocalDate.of(2026, 11, 15)))
        listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳").forEach { rule.onNodeWithText(it).assertExists() }
    }
}
