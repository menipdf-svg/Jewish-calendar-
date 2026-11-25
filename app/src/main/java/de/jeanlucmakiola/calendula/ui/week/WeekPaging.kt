package de.jeanlucmakiola.calendula.ui.week

import de.jeanlucmakiola.calendula.ui.common.CalendarSpan
import de.jeanlucmakiola.calendula.ui.common.startOfWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** A fixed day every grid's pages are counted from. */
private val DAYS_EPOCH = LocalDate(2000, 1, 1)

/** The week the week pager's middle page shows; any date works, it only has to stay fixed. */
private val WEEK_EPOCH = LocalDate(2000, 1, 3)

/**
 * How a timeline pager cuts the calendar: runs of [dayCount] days, offset
 * [phase] days from a fixed epoch, over as many pages as cover [CalendarSpan].
 * The multi-day view's phase (#193) stays put while paging and only changes when
 * a jump moves another date to the first column.
 */
data class DaysGrid(val dayCount: Int, val phase: Int) {

    /** The page showing the epoch; page 0 is the one holding [CalendarSpan.FIRST_DAY]. */
    private val epochPage: Int get() = -pagesFromEpoch(CalendarSpan.FIRST_DAY)

    val pageCount: Int get() = epochPage + pagesFromEpoch(CalendarSpan.LAST_DAY) + 1

    private fun pagesFromEpoch(date: LocalDate): Int =
        Math.floorDiv(DAYS_EPOCH.daysUntil(date) - phase, dayCount)

    /** First day shown on pager [page]. */
    fun startForPage(page: Int): LocalDate =
        DAYS_EPOCH.plus(phase + (page - epochPage) * dayCount, DateTimeUnit.DAY)

    /** The pager page showing [date]. */
    fun pageFor(date: LocalDate): Int =
        (epochPage + pagesFromEpoch(date)).coerceIn(0, pageCount - 1)

    companion object {
        /** Single days: every date starts a page of its own. */
        val days: DaysGrid = DaysGrid(dayCount = 1, phase = 0)

        /** Whole weeks, starting on [firstDay]. */
        fun weeks(firstDay: DayOfWeek): DaysGrid = DaysGrid(
            dayCount = 7,
            phase = DAYS_EPOCH.daysUntil(WEEK_EPOCH.startOfWeek(firstDay)),
        )

        /** The grid of [dayCount]-day pages on which [date] is a page's first day. */
        fun startingAt(date: LocalDate, dayCount: Int): DaysGrid =
            DaysGrid(dayCount, Math.floorMod(DAYS_EPOCH.daysUntil(date), dayCount))
    }
}

/**
 * Where the focused date goes when the pager settles on [page] (#184): kept if
 * the page still shows it, otherwise moved by the pages travelled, so it keeps
 * its place in the page — the same weekday a week on.
 */
internal fun DaysGrid.focusOnPage(focus: LocalDate, page: Int): LocalDate {
    val start = startForPage(page)
    val end = start.plus(dayCount - 1, DateTimeUnit.DAY)
    if (focus in start..end) return focus
    return focus.plus((page - pageFor(focus)) * dayCount, DateTimeUnit.DAY).coerceIn(start, end)
}
