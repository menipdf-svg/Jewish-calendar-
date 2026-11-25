package de.jeanlucmakiola.calendula.ui.common

import kotlinx.datetime.LocalDate

/**
 * The dates every view can show and every date picker offers. One span for all
 * of them, so no view can hand another a date it has no page for (#457).
 */
object CalendarSpan {
    val FIRST_DAY: LocalDate = LocalDate(1600, 1, 1)
    val LAST_DAY: LocalDate = LocalDate(2399, 12, 31)

    val years: IntRange get() = FIRST_DAY.year..LAST_DAY.year
}
