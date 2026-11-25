package de.jeanlucmakiola.calendula.domain

import de.jeanlucmakiola.calendula.domain.ics.parseBasicDate
import de.jeanlucmakiola.calendula.domain.ics.parseBasicDateTime
import de.jeanlucmakiola.calendula.domain.ics.toUtcBasic
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The recurrence shapes the simple picker can express (v1.3): a frequency,
 * an interval, weekly weekday picks, and an optional end. Anything beyond
 * that (ordinal BYDAY like "2TH", BYMONTHDAY, EXDATE rules, …) stays a raw
 * RRULE string the picker shows as "custom" and leaves untouched unless the
 * user replaces it.
 */
data class SimpleRecurrence(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
    /**
     * Weekly only: the weekdays the rule fires on (RRULE BYDAY). Empty means
     * no BYDAY part — the provider derives the day from DTSTART.
     */
    val byDays: Set<DayOfWeek> = emptySet(),
)

enum class RecurrenceFreq {
    Daily,
    Weekly,
    Monthly,
    Yearly,
}

sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd

    /** Last day on which an occurrence may fall (inclusive). */
    data class Until(val date: LocalDate) : RecurrenceEnd

    /** Total number of occurrences, counting the first. */
    data class Count(val times: Int) : RecurrenceEnd
}

/**
 * Parse an RRULE into the picker's simple shape, or null when the rule uses
 * parts the picker can't represent (so the UI preserves the original string).
 * Accepts an optional leading "RRULE:" and an ignored WKST part. A datetime
 * UNTIL is converted from UTC into [zone] before its date is taken, mirroring
 * [toRRule].
 */
fun parseSimpleRecurrence(
    rrule: String,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): SimpleRecurrence? {
    val parts = rruleParts(rrule).associate { part -> (part.key ?: return null) to part.value }
    if (parts.keys.any { it !in setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST", "BYDAY") }) {
        return null
    }

    val freq = RecurrenceFreq.entries.firstOrNull { it.name.equals(parts["FREQ"], ignoreCase = true) }
        ?: return null
    val interval = parts["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null } ?: 1

    // BYDAY is simple only as plain weekday picks on a weekly rule; ordinal
    // forms ("2TH" = second Thursday) and BYDAY on other frequencies are not.
    val byDays = parts["BYDAY"]?.let { raw ->
        if (freq != RecurrenceFreq.Weekly) return null
        raw.split(',').map { token -> rruleDay(token.trim()) ?: return null }.toSet()
    } ?: emptySet()

    val until = parts["UNTIL"]
    val count = parts["COUNT"]
    if (until != null && count != null) return null
    val end = when {
        until != null -> RecurrenceEnd.Until(parseUntilDate(until, zone) ?: return null)
        count != null -> RecurrenceEnd.Count(count.toIntOrNull()?.takeIf { it >= 1 } ?: return null)
        else -> RecurrenceEnd.Never
    }
    return SimpleRecurrence(freq, interval, end, byDays)
}

/**
 * Render as a provider-ready RRULE value (no "RRULE:" prefix —
 * `CalendarContract.Events.RRULE` stores the bare value). UNTIL is written as
 * the end of the chosen day *in [zone]*, expressed in UTC: the recurrence
 * engine has been observed applying UNTIL coarsely after converting it into
 * the event's timezone, so a plain `T235959Z` can leak one extra day for
 * zones ahead of UTC.
 */
fun SimpleRecurrence.toRRule(zone: TimeZone = TimeZone.currentSystemDefault()): String = buildString {
    append("FREQ=")
    append(
        when (freq) {
            RecurrenceFreq.Daily -> "DAILY"
            RecurrenceFreq.Weekly -> "WEEKLY"
            RecurrenceFreq.Monthly -> "MONTHLY"
            RecurrenceFreq.Yearly -> "YEARLY"
        },
    )
    if (interval > 1) append(";INTERVAL=$interval")
    if (freq == RecurrenceFreq.Weekly && byDays.isNotEmpty()) {
        append(";BYDAY=")
        append(
            byDays.sortedBy { it.isoDayNumber }
                .joinToString(",") { RRULE_DAY_CODES.getValue(it) },
        )
    }
    when (val e = end) {
        RecurrenceEnd.Never -> Unit
        is RecurrenceEnd.Until -> {
            append(";UNTIL=")
            append(LocalDateTime(e.date, LocalTime(23, 59, 59)).toInstant(zone).toUtcBasic())
        }
        is RecurrenceEnd.Count -> append(";COUNT=${e.times}")
    }
}

/** One `;`-separated part of an RRULE as written; [key] is null when it has no `KEY=`. */
internal class RrulePart(val raw: String) {
    private val eq = raw.indexOf('=')
    val key: String? = if (eq > 0) raw.substring(0, eq).uppercase() else null
    val value: String = if (eq > 0) raw.substring(eq + 1) else ""
}

/**
 * [rule]'s non-blank parts, after an optional leading "RRULE:". What to do
 * with a malformed one (reject, repair or skip) is the caller's call.
 */
internal fun rruleParts(rule: String): List<RrulePart> =
    rule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.map(::RrulePart)

internal val RRULE_DAY_CODES: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

/** Exact two-letter BYDAY token → weekday; ordinal forms ("2TH") return null. */
private fun rruleDay(token: String): DayOfWeek? =
    RRULE_DAY_CODES.entries.firstOrNull { it.value == token.uppercase() }?.key

/**
 * End an arbitrary RRULE (simple or not) at [untilUtcMillis]: any existing
 * UNTIL/COUNT is dropped, every other part (BYDAY, INTERVAL, …) survives.
 * Used for "delete this and all following occurrences" — the caller passes a
 * moment just before the first occurrence to remove.
 */
fun rruleTruncatedAt(rrule: String, untilUtcMillis: Long): String {
    val kept = rrule.removePrefix("RRULE:").split(';')
        .filter { it.isNotBlank() }
        .filterNot { part ->
            val key = part.substringBefore('=').trim().uppercase()
            key == "UNTIL" || key == "COUNT"
        }
    val untilPart = "UNTIL=" + Instant.fromEpochMilliseconds(untilUtcMillis).toUtcBasic()
    return (kept + untilPart).joinToString(";")
}

/**
 * Date of an RRULE UNTIL value ("20260801" or "20260801T215959Z"). Datetime
 * forms are UTC (RFC 5545); the date is taken after converting into [zone] so
 * a [toRRule]-rendered value round-trips to the day the user picked.
 */
private fun parseUntilDate(raw: String, zone: TimeZone): LocalDate? {
    val date = parseBasicDate(raw) ?: return null
    if (raw.length < 15 || raw[8] != 'T') return date
    return parseBasicDateTime(raw)?.toInstant(TimeZone.UTC)?.toLocalDateTime(zone)?.date
}
