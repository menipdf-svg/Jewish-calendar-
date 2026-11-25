package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaZoneId
import kotlinx.datetime.toJavaZoneOffset
import kotlinx.datetime.toKotlinUtcOffset
import kotlinx.datetime.toLocalDateTime
import java.time.ZonedDateTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/** [id] as a zone, or null when the tz database doesn't know it. */
internal fun zoneOrNull(id: String): TimeZone? = runCatching { TimeZone.of(id) }.getOrNull()

/**
 * The zone this form's wall-clock times mean, matching what the data layer
 * resolves them in at write time: the form's own pinned zone, else [deviceZone].
 */
fun EventForm.resolvedZone(deviceZone: TimeZone): TimeZone =
    timezone?.let(::zoneOrNull) ?: deviceZone

/**
 * This wall-clock time in [zone], at [preferred] when the fall-back hour repeats
 * it and that is one of its two offsets — otherwise the earlier one (#387).
 */
fun LocalDateTime.resolveIn(zone: TimeZone, preferred: UtcOffset?): Instant =
    ZonedDateTime.ofLocal(toJavaLocalDateTime(), zone.toJavaZoneId(), preferred?.toJavaZoneOffset())
        .toInstant().toKotlinInstant()

/** Whether this instant's wall clock in [zone] is one the fall-back hour repeats. */
fun Instant.inRepeatedHour(zone: TimeZone): Boolean {
    val zid = zone.toJavaZoneId()
    return zid.rules.getTransition(java.time.LocalDateTime.ofInstant(toJavaInstant(), zid))?.isOverlap == true
}

/**
 * [at]'s offset in this zone when its wall clock is one the fall-back hour
 * repeats, else null: elsewhere the wall clock alone already names the instant.
 * What a [FormTime.offset] holds (#387).
 */
fun TimeZone.repeatedHourOffsetAt(at: Instant): UtcOffset? = if (at.inRepeatedHour(this)) offsetAt(at) else null

/** This instant as a [FormTime] in [zone], keeping its pass of a repeated hour. */
fun Instant.toFormTime(zone: TimeZone): FormTime = FormTime(toLocalDateTime(zone), zone.repeatedHourOffsetAt(this))

/** This time as an instant in [zone], on its kept pass of a repeated hour. */
fun FormTime.instantIn(zone: TimeZone): Instant = dateTime.resolveIn(zone, offset)

/**
 * This edge of an event moved to [picked] by hand — a picker or a resize handle —
 * as a [FormTime] in [zone]. A time in the spring-forward gap moves on to the
 * real one, as the provider would read it. A wall clock the fall-back hour
 * repeats has two instants; which one it means (#387):
 * - [original]'s, when [picked] is the wall clock the event was opened with, so
 *   a time changed and changed back is the time it was;
 * - else the one that moves the edge in the direction the user moved it:
 *   earlier than this edge's instant when [picked] is before [shownAt] (where
 *   the edge was on screen), later when after;
 * - kept within [notBefore]..[notAfter] (the other edge), and to the ones
 *   [drawable] accepts — where the screen can show it — when any fit.
 */
fun FormTime.repicked(
    picked: LocalDateTime,
    zone: TimeZone,
    shownAt: LocalDateTime = dateTime,
    original: FormTime? = null,
    notBefore: Instant? = null,
    notAfter: Instant? = null,
    drawable: (Instant) -> Boolean = { true },
): FormTime {
    if (picked == dateTime && picked == shownAt) return this
    val offsets = zone.toJavaZoneId().rules.getValidOffsets(picked.toJavaLocalDateTime())
    if (offsets.isEmpty()) return FormTime(picked).instantIn(zone).toFormTime(zone)
    if (offsets.size == 1) return FormTime(picked)
    val passes = offsets.map { picked.toInstant(it.toKotlinUtcOffset()) }.sorted()
    val bounded = passes.filter { (notBefore == null || it >= notBefore) && (notAfter == null || it <= notAfter) }
        .ifEmpty { passes }
    val fitting = bounded.filter(drawable).ifEmpty { bounded }
    if (original?.dateTime == picked && original.offset != null && original.instantIn(zone) in fitting) return original
    val from = instantIn(zone)
    val chosen = when {
        picked < shownAt -> fitting.lastOrNull { it < from }
        picked > shownAt -> fitting.firstOrNull { it > from }
        else -> null
    } ?: fitting.minBy { (it - from).absoluteValue }
    return chosen.toFormTime(zone)
}

/** The form's start as an instant in [zone]. */
fun EventForm.startInstant(zone: TimeZone): Instant = start.instantIn(zone)

/** The form's end as an instant in [zone]. */
fun EventForm.endInstant(zone: TimeZone): Instant = end.instantIn(zone)

/** The form's timed span set to these instants, read in [zone]. */
fun EventForm.withTimes(start: Instant, end: Instant, zone: TimeZone): EventForm =
    copy(start = start.toFormTime(zone), end = end.toFormTime(zone))

/**
 * The form pinned to [zoneId] (null: the device). A kept pass of a repeated hour
 * was one of the old zone's, so it goes when the zone the times mean changes.
 */
fun EventForm.withTimezone(zoneId: String?, deviceZone: TimeZone): EventForm {
    val pinned = copy(timezone = zoneId)
    if (pinned.resolvedZone(deviceZone) == resolvedZone(deviceZone)) return pinned
    return pinned.withoutKeptPasses()
}

/**
 * The form with its start picked by hand, keeping its length. A repeated hour's
 * pass is [repicked]: [original] is the start the event was opened with.
 */
fun EventForm.withStart(start: LocalDateTime, zone: TimeZone, original: FormTime? = null): EventForm {
    val span = endInstant(zone) - startInstant(zone)
    val startAt = this.start.repicked(start, zone, original = original).instantIn(zone)
    return withTimes(startAt, startAt + span, zone)
}

/**
 * The form with its end picked by hand. A repeated hour's pass is [repicked],
 * kept after the start where it can be: [original] is the end the event was
 * opened with.
 */
fun EventForm.withEnd(end: LocalDateTime, zone: TimeZone, original: FormTime? = null): EventForm =
    copy(end = this.end.repicked(end, zone, original = original, notBefore = startInstant(zone)))

/** The form with no kept passes: its times as the wall clock alone says them. */
fun EventForm.withoutKeptPasses(): EventForm = copy(start = FormTime(start.dateTime), end = FormTime(end.dateTime))

/**
 * The form moved so it starts at [newStart], keeping its **instant** duration —
 * a recurring event's length travels to the provider as `DURATION`, so keeping
 * wall clock instead would rewrite the series' length across a DST boundary.
 *
 * All-day events are date-anchored — use [shiftedByDays]; this returns them
 * untouched.
 */
fun EventForm.shiftedTo(newStart: Instant, deviceZone: TimeZone): EventForm {
    if (isAllDay) return this
    val zone = resolvedZone(deviceZone)
    val span = endInstant(zone) - startInstant(zone)
    return withTimes(newStart, newStart + span, zone)
}

/**
 * The form moved [days] calendar days, keeping its time of day and its span.
 * All-day events are pure date arithmetic; a timed one preserves its **instant**
 * duration, for the same reason [shiftedTo] does.
 */
fun EventForm.shiftedByDays(days: Int, deviceZone: TimeZone): EventForm {
    if (days == 0) return this
    val newStart = LocalDateTime(start.date.plus(days, DateTimeUnit.DAY), start.time)
    if (isAllDay) {
        return copy(
            start = FormTime(newStart),
            end = FormTime(LocalDateTime(end.date.plus(days, DateTimeUnit.DAY), end.time)),
        )
    }
    val zone = resolvedZone(deviceZone)
    val span = endInstant(zone) - startInstant(zone)
    val startAt = FormTime(newStart, start.offset).instantIn(zone)
    // The start keeps the wall clock it was moved to, even one the spring-forward
    // gap skips, so a recurring series still sees a whole-day move.
    val movedStart = startAt.toFormTime(zone).takeIf { it.dateTime == newStart } ?: FormTime(newStart)
    return copy(start = movedStart, end = (startAt + span).toFormTime(zone))
}

/**
 * The form with new start and end, each in the form's own zone — a resize. An
 * all-day event becomes timed: one dragged out of the all-day strip.
 */
fun EventForm.resizedTo(newStart: Instant, newEnd: Instant, deviceZone: TimeZone): EventForm {
    val timed = copy(isAllDay = false)
    return timed.withTimes(newStart, newEnd, timed.resolvedZone(deviceZone))
}
