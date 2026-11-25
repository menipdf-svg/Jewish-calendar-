package de.jeanlucmakiola.calendula.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.jeanlucmakiola.floret.time.rowWeekNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.temporal.WeekFields

/**
 * The week number of the grid row starting on [rowStart], numbered by the
 * device region's rules (#493). The region, not the app language: an English
 * UI in the Netherlands still numbers weeks the ISO way. Shared by the Month
 * gutter and the Week badge so the two always agree.
 */
@Composable
internal fun rememberRowWeekNumber(rowStart: LocalDate): Int {
    val fields = remember { WeekFields.of(Resources.getSystem().configuration.locales[0]) }
    return remember(rowStart, fields) { rowStart.toJavaLocalDate().rowWeekNumber(fields) }
}
