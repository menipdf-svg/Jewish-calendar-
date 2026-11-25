package de.jeanlucmakiola.calendula.widget.countdown

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.coversWholeDays
import de.jeanlucmakiola.calendula.domain.isCrossedOut
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What a countdown widget counts down to (#439). */
enum class CountdownMode { NEXT, PINNED }

data class CountdownOptions(
    val mode: CountdownMode = CountdownMode.NEXT,
    /** The pinned event or series; a series follows its next occurrence. */
    val eventId: Long? = null,
    /** The pinned event's title when it was chosen, for the config screen. */
    val title: String? = null,
)

enum class CountdownUnit { NOW, MINUTES, HOURS, DAYS }

/** "in 25 min", "ends in 3 h", "in 12 days": [running] counts to the end instead of the start. */
data class CountdownLabel(val running: Boolean, val unit: CountdownUnit, val amount: Int)

private fun target(event: EventInstance, now: Instant): Instant = if (event.start <= now) event.end else event.start

/**
 * The label for [event] at [now]. Under a minute reads "now", under an hour
 * counts minutes (rounded up), under a day whole hours (rounded), and beyond
 * that calendar days in [zone], so "in 1 day" is always tomorrow.
 */
fun countdownLabel(event: EventInstance, now: Instant, zone: TimeZone): CountdownLabel {
    val running = event.start <= now
    val target = target(event, now)
    val delta = target - now
    return when {
        delta < 1.minutes -> CountdownLabel(running, CountdownUnit.NOW, 0)
        delta < 1.hours -> CountdownLabel(running, CountdownUnit.MINUTES, ceil(delta / 1.minutes).toInt())
        delta < 24.hours -> CountdownLabel(running, CountdownUnit.HOURS, (delta / 1.hours).roundToInt().coerceIn(1, 23))
        else -> {
            val days = now.toLocalDateTime(zone).date.daysUntil(target.toLocalDateTime(zone).date)
            CountdownLabel(running, CountdownUnit.DAYS, days.coerceAtLeast(1))
        }
    }
}

/**
 * When [countdownLabel] next reads differently, so the widget redraws then
 * and not before: every minute in the last hour, at each half hour within the
 * day, and at midnight or the 24-hour mark beyond that.
 */
fun nextLabelChange(event: EventInstance, now: Instant, zone: TimeZone): Instant {
    val target = target(event, now)
    val label = countdownLabel(event, now, zone)
    val lowerBound: Duration = when (label.unit) {
        CountdownUnit.NOW -> Duration.ZERO
        CountdownUnit.MINUTES -> (label.amount - 1).minutes
        CountdownUnit.HOURS -> maxOf((label.amount - 0.5).hours, 1.hours)
        CountdownUnit.DAYS -> {
            val midnight = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            return minOf(midnight, target - 24.hours).coerceAtLeast(now + 1.minutes)
        }
    }
    return (target - lowerBound).coerceAtLeast(now + 1.minutes)
}

/**
 * The next-event mode's pick: timed and not filling whole days (#296), not
 * declined or cancelled, not over; of those, the one whose countdown changes
 * soonest — a running event's end, else a start.
 */
fun pickNextEvent(instances: List<EventInstance>, now: Instant, zone: TimeZone): EventInstance? =
    instances
        .filter { !it.coversWholeDays(zone) && !it.isCrossedOut && it.end > now }
        .minByOrNull { target(it, now) }

/** A pinned event's occurrence to count to: the running or next one that is not cancelled (#388). */
fun pickPinnedOccurrence(occurrences: List<EventInstance>, now: Instant, zone: TimeZone): EventInstance? =
    occurrences.filter { it.status != EventStatus.Cancelled && it.counted(zone).end > now }.minByOrNull { it.start }

/**
 * The span a countdown counts over. An all-day event is stored on UTC
 * midnights; it starts and ends at local midnight of those dates instead.
 */
fun EventInstance.counted(zone: TimeZone): EventInstance = if (!isAllDay) this else copy(
    start = start.toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(zone),
    end = end.toLocalDateTime(TimeZone.UTC).date.atStartOfDayIn(zone),
)
