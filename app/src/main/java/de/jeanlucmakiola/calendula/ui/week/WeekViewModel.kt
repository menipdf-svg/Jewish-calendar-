package de.jeanlucmakiola.calendula.ui.week

import de.jeanlucmakiola.calendula.ui.common.dayRange
import de.jeanlucmakiola.calendula.ui.common.renderedSpan
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.data.time.TodaySource
import de.jeanlucmakiola.calendula.data.time.ZoneSource
import de.jeanlucmakiola.calendula.data.time.currentDate
import de.jeanlucmakiola.calendula.data.time.stateFollowingDays
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.coversWholeDays
import de.jeanlucmakiola.calendula.domain.displayPriority
import de.jeanlucmakiola.calendula.domain.displayStart
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.calendarListFailure
import de.jeanlucmakiola.calendula.ui.common.PageStateCache
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinUtcOffset
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant
import javax.inject.Inject
import kotlin.math.abs

const val MINUTES_PER_DAY: Int = 24 * 60

/** How far from the requested page [PagedTimelineViewModel.span] keeps other pages cached. */
private const val SPAN_CACHE_DAYS = 28

/** A run of [count] days from [start]: what one timeline page shows. */
data class DaySpan(val start: LocalDate, val count: Int)

/** How the pager cuts the calendar, and the page the anchor sits on. */
data class PagerPosition(val grid: DaysGrid, val page: Int)

/** Where [anchor] sits under [rule]. */
fun positionOf(anchor: LocalDate, rule: (LocalDate) -> DaysGrid): PagerPosition =
    rule(anchor).let { PagerPosition(it, it.pageFor(anchor)) }

/**
 * A timeline paged in runs of days: the week view, and the multi-day view (#193).
 * The pager leads; the anchor follows it once it settles, and the grid it is
 * cut by is derived from the anchor through [gridRule], so a subclass decides
 * whether a jump re-frames the pages or only moves between them.
 *
 * @param gridRule the grid to use for a given anchor, as the settings behind it change.
 * @param initialRule the rule used until [gridRule] first emits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PagedTimelineViewModel(
    private val repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    todaySource: TodaySource,
    zoneSource: ZoneSource,
    private val io: CoroutineDispatcher,
    gridRule: Flow<(LocalDate) -> DaysGrid>,
    initialRule: (LocalDate) -> DaysGrid,
) : ViewModel() {

    /**
     * The zone every page is laid out in; drags and drops resolve in it too, and
     * pages lay out again when the device moves zones (#387).
     */
    val zone: StateFlow<TimeZone> = zoneSource.zone
        .stateIn(viewModelScope, SharingStarted.Eagerly, zoneSource.current())

    /** Whether to fade events that have already finished (display concern only). */
    val dimCompletedEvents: StateFlow<Boolean> = settingsPrefs.dimCompletedEvents
        .stateWhileSubscribed(
            scope = viewModelScope,
            initialValue = false,
        )

    /** How long a block drafted on an empty slot starts out (#153). */
    val defaultEventDuration: StateFlow<Int> = settingsPrefs.defaultEventDurationMinutes
        .stateWhileSubscribed(
            scope = viewModelScope,
            initialValue = SettingsPrefs.DEFAULT_EVENT_DURATION,
        )

    private val todayDate: LocalDate
        get() = currentDate()

    // A day on the visible page; the page itself comes from the grid, so a
    // settings change re-frames the same days instead of jumping.
    private val _anchor = MutableStateFlow(todayDate)
    val anchor: StateFlow<LocalDate> = _anchor

    /**
     * The grid for a given anchor, held eagerly so a screen can place its pager
     * on its very first frame rather than a frame after the anchor moved.
     */
    val rule: StateFlow<(LocalDate) -> DaysGrid> =
        gridRule.stateIn(viewModelScope, SharingStarted.Eagerly, initialRule)

    /** Today, kept current across midnight so a page left open doesn't mark yesterday (#373). */
    private val today: StateFlow<LocalDate> =
        todaySource.today.stateFollowingDays(viewModelScope, todayDate, ::followToday)

    private val position: StateFlow<PagerPosition> =
        combine(_anchor, rule) { anchor, rule -> positionOf(anchor, rule) }
            .distinctUntilChanged()
            .stateWhileSubscribed(
                scope = viewModelScope,
                initialValue = positionOf(todayDate, initialRule),
            )

    /**
     * The anchor page's state. Once loaded it never falls back to [WeekUiState.Loading]:
     * moving the anchor keeps the last result until the new page arrives, since
     * the pages draw their own days and this only gates the failure screen.
     */
    val state: StateFlow<WeekUiState> =
        position.map { DaySpan(it.grid.startForPage(it.page), it.grid.dayCount) }
            .distinctUntilChanged()
            .flatMapLatest { span -> span(span).filterNot { it is WeekUiState.Loading } }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .stateWhileSubscribed(
                scope = viewModelScope,
                initialValue = WeekUiState.Loading,
            )

    private val spans = PageStateCache<DaySpan, WeekUiState>(
        scope = viewModelScope,
        initial = WeekUiState.Loading,
        // Far enough to cover the pages either side and a swipe back, so paging
        // through a year doesn't keep a year of queries around.
        keep = { cached, requested ->
            cached.count == requested.count &&
                abs(cached.start.daysUntil(requested.start)) <= SPAN_CACHE_DAYS
        },
    ) { span ->
        zone.flatMapLatest { zone ->
            combine(
                repository.calendars(),
                repository.instances(spanRange(span.start, span.count, zone)),
                today,
            ) { calendars, instances, today ->
                buildState(span, calendars, instances, today, zone)
            }
        }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
    }

    /** The days of one page, shared between that page and [state]. Main thread only. */
    fun span(span: DaySpan): StateFlow<WeekUiState> = spans.get(span)

    /** The pager came to rest on [page]; follow it unless it is already the anchor's. */
    fun onPageSettled(page: Int) {
        val grid = rule.value(_anchor.value)
        if (grid.pageFor(_anchor.value) != page) _anchor.value = grid.startForPage(page)
    }

    /** Move the anchor to [date], re-framing the pages if the grid depends on it. */
    fun jumpTo(date: LocalDate) {
        _anchor.value = date
    }

    fun goToToday() = jumpTo(todayDate)

    /** The date rolled over; a page still showing [old] follows on to [new], one browsed elsewhere stays. */
    private fun followToday(old: LocalDate, new: LocalDate) {
        val anchor = _anchor.value
        val grid = rule.value(anchor)
        if (grid.pageFor(anchor) == grid.pageFor(old)) _anchor.value = new
    }

    private fun buildState(
        span: DaySpan,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
        today: LocalDate,
        zone: TimeZone,
    ): WeekUiState {
        calendarListFailure(calendars)?.let { return WeekUiState.Failure(it) }
        val days = (0 until span.count).map { span.start.plus(it, DateTimeUnit.DAY) }
        val (allDay, timed) = instances.splitWholeDays(zone)
        return WeekUiState.Success(
            weekStart = span.start,
            today = today,
            days = days,
            allDaySpans = layoutAllDay(allDay, days, zone),
            timedByDay = days.associateWith { day -> layoutDay(timed, day, zone) },
        )
    }
}

/** Whole weeks, starting on the first day from Settings (AUTO → locale). */
@HiltViewModel
class WeekViewModel @Inject constructor(
    repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    todaySource: TodaySource,
    zoneSource: ZoneSource,
    @IoDispatcher io: CoroutineDispatcher,
) : PagedTimelineViewModel(
    repository = repository,
    settingsPrefs = settingsPrefs,
    todaySource = todaySource,
    zoneSource = zoneSource,
    io = io,
    gridRule = settingsPrefs.weekStart.map { pref ->
        val grid = DaysGrid.weeks(pref.resolveFirstDay(Locale.getDefault()))
        val rule: (LocalDate) -> DaysGrid = { grid }
        rule
    },
    initialRule = { DaysGrid.weeks(DayOfWeek.MONDAY) },
)

/**
 * The multi-day view (#193): runs of the day count from Settings, with the
 * anchor always a page's first day, so today — or a date jumped to — opens in
 * the first column.
 */
@HiltViewModel
class MultiDayViewModel @Inject constructor(
    repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    todaySource: TodaySource,
    zoneSource: ZoneSource,
    @IoDispatcher io: CoroutineDispatcher,
) : PagedTimelineViewModel(
    repository = repository,
    settingsPrefs = settingsPrefs,
    todaySource = todaySource,
    zoneSource = zoneSource,
    io = io,
    gridRule = settingsPrefs.multiDayCount.map { count ->
        { anchor: LocalDate -> DaysGrid.startingAt(anchor, count) }
    },
    initialRule = { DaysGrid.startingAt(it, SettingsPrefs.DEFAULT_MULTI_DAY_COUNT) },
)

/** The day view: the timeline one day at a time. */
@HiltViewModel
class DayViewModel @Inject constructor(
    repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    todaySource: TodaySource,
    zoneSource: ZoneSource,
    @IoDispatcher io: CoroutineDispatcher,
) : PagedTimelineViewModel(
    repository = repository,
    settingsPrefs = settingsPrefs,
    todaySource = todaySource,
    zoneSource = zoneSource,
    io = io,
    gridRule = flowOf { _: LocalDate -> DaysGrid.days },
    initialRule = { DaysGrid.days },
)

/**
 * Lay out all-day events as connected horizontal spans across the visible week.
 * Each event becomes one [AllDaySpan] from its first to its last covered column;
 * overlapping spans are stacked on separate lanes (greedy first-fit).
 */
internal fun layoutAllDay(
    events: List<EventInstance>,
    days: List<LocalDate>,
    zone: TimeZone,
): List<AllDaySpan> {
    data class Raw(val event: EventInstance, val startCol: Int, val endCol: Int, val priority: Int)

    val raw = events
        .mapNotNull { ev ->
            val covered = days.indices.filter { ev.coversDay(days[it], zone) }
            if (covered.isEmpty()) null else Raw(ev, covered.first(), covered.last(), ev.displayPriority(zone))
        }
        // Declined bars are packed after every other one, so on any day they
        // cover they land in a lane below it (#230).
        .sortedWith(
            compareBy<Raw> { it.priority }
                .thenBy { it.startCol }
                .thenBy { it.endCol }
                .thenBy { it.event.displayStart(zone) }
                .thenBy { it.event.title }
                .thenBy { it.event.instanceId },
        )

    // What each lane already holds, rather than just how far right it reaches.
    // A single "last occupied column" only answers correctly while spans arrive
    // in non-decreasing start order, which the declined-last rule above breaks:
    // a Monday bar seated after a Wednesday one would be refused a lane it is
    // nowhere near, and each wasted lane costs the all-day strip a whole row and
    // pushes a bar closer to the month grid's lane cap. Seven columns
    // and a handful of bars, so the scan is cheaper than the sort above it.
    val laneCols = ArrayList<MutableList<IntRange>>()
    return raw.map { r ->
        val cols = r.startCol..r.endCol
        var lane = laneCols.indexOfFirst { seated -> seated.none { it overlaps cols } }
        if (lane == -1) {
            laneCols.add(mutableListOf(cols))
            lane = laneCols.size - 1
        } else {
            laneCols[lane].add(cols)
        }
        AllDaySpan(r.event, r.startCol, r.endCol, lane)
    }
}

/** Whether two column ranges share a column, so they cannot share a lane. */
private infix fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

/** Instant range covering the [count] days starting at [start]. */
internal fun spanRange(start: LocalDate, count: Int, zone: TimeZone): ClosedRange<Instant> =
    dayRange(start, start.plus(count - 1, DateTimeUnit.DAY), zone)

/**
 * This instant's wall-clock time as minutes past [day]'s midnight in [zone] —
 * negative before that day, past 1440 after it. Elapsed time since midnight would
 * put everything after a DST change an hour off on that day's grid (#387).
 */
internal fun Instant.minutesPast(day: LocalDate, zone: TimeZone): Int {
    val local = toLocalDateTime(zone)
    val days = (local.date.toEpochDays() - day.toEpochDays()).toInt()
    return days * MINUTES_PER_DAY + local.time.toSecondOfDay() / 60
}

/** [instant]'s [minutesPast] clamped to [day]. */
internal fun wallClockMinutes(instant: Instant, day: LocalDate, zone: TimeZone): Int =
    instant.minutesPast(day, zone).coerceIn(0, MINUTES_PER_DAY)

/**
 * Where a run from [start] to [end] that begins at [startMin] ends on [day]'s
 * wall clock. A run that began before the fall-back hour's repeat and ends
 * inside it is drawn to where that hour ends (03:00 in Berlin), since its own
 * wall-clock end reads earlier than its real length (#387).
 */
internal fun wallClockEndMinutes(
    startMin: Int,
    start: Instant,
    end: Instant,
    day: LocalDate,
    zone: TimeZone,
): Int {
    val wall = end.minutesPast(day, zone)
    if (end <= start) return maxOf(wall, startMin)
    val drawn = repeatedHourEndMinutes(start, end, day, zone) ?: wall
    return if (drawn > startMin) drawn else startMin + (end - start).inWholeMinutes.toInt()
}

/**
 * Where the fall-back hour's repeat [end] falls in ends, as minutes past [day]'s
 * midnight: the wall clock the zone fell back from (03:00 in Berlin). Null unless
 * [end] is in that repeat and [start] before it.
 */
private fun repeatedHourEndMinutes(start: Instant, end: Instant, day: LocalDate, zone: TimeZone): Int? {
    // Just past [end], so an end on the transition itself still finds it.
    val after = java.time.Instant.ofEpochMilli(end.toEpochMilliseconds() + 1)
    val transition = zone.toJavaZoneId().rules.previousTransition(after)
        ?.takeIf { it.isOverlap } ?: return null
    val fellBack = transition.instant.toKotlinInstant()
    if (start >= fellBack || end >= fellBack + transition.duration.abs().toKotlinDuration()) return null
    // The moment it fell back, read on the clock it fell back from.
    return fellBack.minutesPast(day, FixedOffsetTimeZone(transition.offsetBefore.toKotlinUtcOffset()))
}

/**
 * The end a block's time label names on [day]: its event's own wall-clock end,
 * which for one ending in the fall-back hour's repeat is earlier than the
 * [TimedBlock.endMin] it is drawn to (#387).
 */
internal fun TimedBlock.labelEndMin(day: LocalDate, zone: TimeZone): Int =
    minOf(endMin, wallClockMinutes(event.end, day, zone))

/** True if this event overlaps the calendar [day] in [zone] (any portion). */
internal fun EventInstance.coversDay(day: LocalDate, zone: TimeZone): Boolean {
    if (isAllDay) {
        // All-day events live at UTC midnights with an exclusive end. Compare
        // calendar dates in UTC and step the exclusive end back to the last
        // covered day (mirroring the detail/edit views), so a one-day event
        // covers exactly its single date. Slicing the day in the device zone
        // would push the exclusive end a few hours into the next local day
        // east of UTC, making the event leak onto day + 1.
        val startDate = start.toLocalDateTime(TimeZone.UTC).date
        val endExclusive = end.toLocalDateTime(TimeZone.UTC).date
        val lastDay = maxOf(startDate, endExclusive.minus(1, DateTimeUnit.DAY))
        return day in startDate..lastDay
    }
    val dayStart = day.atStartOfDayIn(zone)
    val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return start < dayEnd && end > dayStart
}

/**
 * True if this event touches more than one calendar day in [zone] — an all-day
 * event covering a range, or a timed one crossing midnight. Occurrences are
 * contiguous, so covering the day after the first is the whole question.
 */
internal fun EventInstance.spansMultipleDays(zone: TimeZone): Boolean {
    val anchor = if (isAllDay) TimeZone.UTC else zone
    val secondDay = start.toLocalDateTime(anchor).date.plus(1, DateTimeUnit.DAY)
    return coversDay(secondDay, zone)
}

/**
 * Split events into those for the all-day strip and those for the time grid:
 * all-day events and timed ones from midnight to midnight go to the strip (#296).
 */
internal fun List<EventInstance>.splitWholeDays(zone: TimeZone): Pair<List<EventInstance>, List<EventInstance>> =
    partition { it.coversWholeDays(zone) }

/**
 * Clip [events] to a single [day] and assign lanes so overlapping events render
 * side-by-side. Lane count is computed per overlap-cluster (a maximal run of
 * chained-overlapping events), matching the common phone week-view behaviour.
 * Overlap is judged on each block's [renderedSpan], so a short block's floor
 * (lifted off midnight, #483) never hides another block.
 *
 * Takes the timed half of [splitWholeDays]; the rest lives in the all-day strip.
 */
internal fun layoutDay(
    events: List<EventInstance>,
    day: LocalDate,
    zone: TimeZone,
): List<TimedBlock> {
    val dayStart = day.atStartOfDayIn(zone)
    val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

    val raw = events.asSequence()
        .mapNotNull { ev ->
            if (ev.start == ev.end) {
                // Instant event: keep only if the point falls inside this day.
                if (ev.start < dayStart || ev.start >= dayEnd) return@mapNotNull null
                val m = wallClockMinutes(ev.start, day, zone)
                TimedBlock(ev, m, m, lane = 0, laneCount = 1)
            } else {
                val s = maxOf(ev.start, dayStart)
                val e = minOf(ev.end, dayEnd)
                if (e <= s) return@mapNotNull null
                val startMin = wallClockMinutes(s, day, zone)
                val endMin = wallClockEndMinutes(startMin, s, e, day, zone).coerceAtMost(MINUTES_PER_DAY)
                TimedBlock(ev, startMin, endMin, lane = 0, laneCount = 1, continuesAfter = ev.end > dayEnd)
            }
        }
        .map { it to it.renderedSpan() }
        .sortedWith(compareBy({ it.second.topMin }, { it.second.bottomMin }))
        .toList()

    val result = ArrayList<TimedBlock>(raw.size)
    var i = 0
    while (i < raw.size) {
        // Grow a cluster of chained-overlapping events.
        var clusterEnd = raw[i].second.bottomMin
        var j = i + 1
        while (j < raw.size && raw[j].second.topMin < clusterEnd) {
            clusterEnd = maxOf(clusterEnd, raw[j].second.bottomMin)
            j++
        }
        val cluster = raw.subList(i, j)
        // Greedy first-fit column assignment (= max overlap depth in the cluster).
        val laneEnd = ArrayList<Float>()
        val lanes = IntArray(cluster.size)
        cluster.forEachIndexed { k, (_, span) ->
            var placed = laneEnd.indexOfFirst { it <= span.topMin }
            if (placed == -1) {
                laneEnd.add(span.bottomMin)
                placed = laneEnd.size - 1
            } else {
                laneEnd[placed] = span.bottomMin
            }
            lanes[k] = placed
        }
        val laneCount = laneEnd.size
        cluster.forEachIndexed { k, (block, _) ->
            result.add(block.copy(lane = lanes[k], laneCount = laneCount))
        }
        i = j
    }
    return result
}
