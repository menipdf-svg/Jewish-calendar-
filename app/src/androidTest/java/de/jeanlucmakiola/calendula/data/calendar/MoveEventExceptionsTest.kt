package de.jeanlucmakiola.calendula.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.toEditForm
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Moving a synced series keeps its deleted and edited occurrences, although the
 * moved row has no `_sync_id` for exception rows to attach to.
 */
@RunWith(AndroidJUnit4::class)
class MoveEventExceptionsTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val ds = AndroidCalendarDataSource(context)
    private val zone = TimeZone.currentSystemDefault()

    private val day = 86_400_000L
    private val start = 1_893_492_000_000L // 2030-01-01T10:00Z
    private val hour = 3_600_000L

    /** "day offset:title" of every instance on [calendarId]. */
    private fun occurrences(calendarId: Long): List<String> =
        ds.instances(start - day, start + 10 * day)
            .filter { it.calendarId == calendarId }
            .map { "${(it.start.toEpochMilliseconds() - start) / day}:${it.title}" }
            .sorted()

    @Test
    fun movedSeriesKeepsCancelledAndModifiedOccurrences() {
        val a = ds.createLocalCalendar("move-test-a", 0xFF336699.toInt(), null)
        val b = ds.createLocalCalendar("move-test-b", 0xFF993366.toInt(), null)
        try {
            val src = insertSyncedSeries(a)
            ds.deleteOccurrence(src, start + 2 * day)
            val occurrence = ds.eventDetail(src, 540)!!.toEditForm(start + 3 * day, start + 3 * day + hour, zone)
            ds.updateOccurrence(src, start + 3 * day, occurrence, occurrence.copy(title = "edited"), 540)
            val expected = listOf("0:series", "1:series", "3:edited", "4:series")
            assertThat(occurrences(a)).isEqualTo(expected)

            val form = ds.eventDetail(src, 540)!!.toEditForm(start, start + hour, zone)
            ds.moveEvent(src, b, form, form, 540)

            assertThat(occurrences(a)).isEmpty()
            assertThat(occurrences(b)).isEqualTo(expected)
        } finally {
            ds.deleteCalendar(a)
            ds.deleteCalendar(b)
        }
    }

    /** A daily series carrying a `_sync_id`, so its exceptions are real exception rows. */
    private fun insertSyncedSeries(calendarId: Long): Long {
        val accountName = resolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
            arrayOf(CalendarContract.Calendars.ACCOUNT_NAME), null, null, null,
        )!!.use { it.moveToFirst(); it.getString(0) }
        val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "series")
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DURATION, "P3600S")
            put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=5")
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            put(CalendarContract.Events._SYNC_ID, "move-test-series")
        }
        return ContentUris.parseId(resolver.insert(uri, values)!!)
    }
}
