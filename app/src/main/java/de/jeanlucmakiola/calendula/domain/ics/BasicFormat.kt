package de.jeanlucmakiola.calendula.domain.ics

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** RFC 5545 basic format, `yyyyMMddTHHmmss`. */
private val BASIC_DATE_TIME = LocalDateTime.Format {
    date(LocalDate.Formats.ISO_BASIC)
    char('T')
    hour()
    minute()
    second()
}

/** `yyyyMMdd`. */
internal fun LocalDate.toBasic(): String = format(LocalDate.Formats.ISO_BASIC)

/** `yyyyMMddTHHmmss`, floating. */
internal fun LocalDateTime.toBasic(): String = format(BASIC_DATE_TIME)

/** `yyyyMMddTHHmmssZ`, the instant in UTC. */
internal fun Instant.toUtcBasic(): String = toLocalDateTime(TimeZone.UTC).toBasic() + "Z"

/**
 * The date in the first eight characters of a basic-format value; whatever
 * follows (a time, a `Z`) is ignored. Null when those aren't a valid date.
 */
internal fun parseBasicDate(s: String): LocalDate? = runCatching {
    LocalDate(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
}.getOrNull()

/** `yyyyMMddTHHmm[ss]`; the separator isn't checked and missing seconds read as zero. */
internal fun parseBasicDateTime(s: String): LocalDateTime? = runCatching {
    val date = parseBasicDate(s) ?: return null
    val time = LocalTime(
        s.substring(9, 11).toInt(),
        s.substring(11, 13).toInt(),
        if (s.length >= 15) s.substring(13, 15).toInt() else 0,
    )
    LocalDateTime(date, time)
}.getOrNull()
