package de.jeanlucmakiola.calendula.data.calendar

import android.database.Cursor
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.ics.parseRfc2445DurationMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Read-only view over a single row's columns by index. Lets the mappers work
 * on pure-Kotlin test fixtures (MapColumnReader) on the JVM, while the
 * production path adapts an Android Cursor row via CursorColumnReader.
 */
internal interface ColumnReader {
    fun getLong(index: Int): Long
    fun getString(index: Int): String?
    fun getInt(index: Int): Int
    fun isNull(index: Int): Boolean
}

internal class CursorColumnReader(private val cursor: Cursor) : ColumnReader {
    override fun getLong(index: Int): Long = cursor.getLong(index)
    override fun getString(index: Int): String? = cursor.getString(index)
    override fun getInt(index: Int): Int = cursor.getInt(index)
    override fun isNull(index: Int): Boolean = cursor.isNull(index)
}

internal fun ColumnReader.getIntOrNull(index: Int): Int? = if (isNull(index)) null else getInt(index)

internal fun ColumnReader.getLongOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)

/**
 * The row's end: DTEND, or DTSTART + DURATION for a recurring row, which stores
 * no DTEND. Unclamped; a missing/blank both means a zero-length event.
 */
internal fun ColumnReader.endMillis(dtStartMillis: Long, dtEndIndex: Int, durationIndex: Int): Long =
    getLongOrNull(dtEndIndex) ?: (dtStartMillis + parseRfc2445DurationMillis(getString(durationIndex)))

/** STATUS shares value 0 with TENTATIVE, so an absent column must read as Confirmed. */
internal fun ColumnReader.eventStatus(index: Int): EventStatus =
    getIntOrNull(index)?.let(::mapEventStatus) ?: EventStatus.Confirmed

/** Map every row, reading each through one [CursorColumnReader]. */
internal inline fun <T> Cursor.mapRows(map: ColumnReader.() -> T): List<T> {
    val reader = CursorColumnReader(this)
    return buildList { while (moveToNext()) add(reader.map()) }
}

/** [mapRows], dropping the rows [map] returns null for. */
internal inline fun <T : Any> Cursor.mapRowsNotNull(map: ColumnReader.() -> T?): List<T> {
    val reader = CursorColumnReader(this)
    return buildList { while (moveToNext()) reader.map()?.let(::add) }
}

/** The UTC calendar day of [millis] — an all-day row's date, since it sits at UTC midnight. */
internal fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
