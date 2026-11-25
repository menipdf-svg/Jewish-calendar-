package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/** Lower values appear first in lists and get the upper visible lanes in grids. */
fun EventInstance.displayPriority(zone: TimeZone): Int = when {
    isCrossedOut -> 3
    spansMultipleDays(zone) -> 0
    coversWholeDays(zone) -> 1
    else -> 2
}

/**
 * Shared order for agenda and split day lists. Grid bars add their lane geometry.
 * Each event's priority is worked out once, not on every comparison.
 */
fun List<EventInstance>.sortedForDisplay(zone: TimeZone): List<EventInstance> =
    map { Triple(it, it.displayPriority(zone), it.displayStart(zone)) }
        .sortedWith(
            compareBy<Triple<EventInstance, Int, Instant>> { it.second }
                .thenBy { it.third }
                .thenBy { it.first.title }
                .thenBy { it.first.instanceId },
        )
        .map { it.first }

/**
 * Where an event starts for ordering: a whole-day event at local midnight of its
 * first day, since an all-day one is stored at UTC midnight and would otherwise
 * sort around timed events by the device's offset (#296).
 */
internal fun EventInstance.displayStart(zone: TimeZone): Instant =
    if (coversWholeDays(zone)) spanFirstDay(zone).atStartOfDayIn(zone) else start
