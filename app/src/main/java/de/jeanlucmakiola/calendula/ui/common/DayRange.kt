package de.jeanlucmakiola.calendula.ui.common

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** Instant range from the start of [first] through the last second of [last] in [zone]. */
internal fun dayRange(first: LocalDate, last: LocalDate, zone: TimeZone): ClosedRange<Instant> =
    first.atStartOfDayIn(zone)..last.atTime(23, 59, 59).toInstant(zone)

/** Beginning of the week (at [weekStart]) that contains this date. */
internal fun LocalDate.startOfWeek(weekStart: DayOfWeek): LocalDate {
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → identical to ISO ordering.
    val offset = ((dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    return minus(offset, DateTimeUnit.DAY)
}
