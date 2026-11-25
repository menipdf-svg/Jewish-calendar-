package de.jeanlucmakiola.calendula.widget.month

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Parcel
import android.provider.CalendarContract
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import de.jeanlucmakiola.calendula.R
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the month widget for a packed month through Glance's own translator
 * (#372) and checks what one size costs on the wire: the host's binder buffer is
 * 1 MB for every widget update at once, and Exact sends one copy per size (#214).
 */
@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class MonthWidgetPayloadTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var calendarId = -1L

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    @Before
    fun packTheMonth() {
        val calendar = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF3F51B5.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(), calendar)
        calendarId = ContentUris.parseId(requireNotNull(uri))

        val zone = ZoneId.systemDefault()
        val first = LocalDate.now(zone).withDayOfMonth(1).minusDays(7)
        val events = (0 until 49).flatMap { offset ->
            val day = first.plusDays(offset.toLong())
            (0 until EVENTS_PER_DAY).map { i ->
                val start = day.atTime(8 + i, 0).atZone(zone).toInstant().toEpochMilli()
                ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    put(CalendarContract.Events.TITLE, "Busy $i")
                    put(CalendarContract.Events.DTSTART, start)
                    put(CalendarContract.Events.DTEND, start + 30 * 60_000L)
                    put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                }
            }
        }
        context.contentResolver.bulkInsert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), events.toTypedArray())
    }

    @After
    fun removeCalendar() {
        if (calendarId < 0) return
        context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId).asSyncAdapter(),
            null,
            null,
        )
    }

    @Test
    fun aPackedMonthRendersWithinItsPayload() = runBlocking {
        for (size in listOf(DpSize(250.dp, 250.dp), DpSize(320.dp, 360.dp), DpSize(420.dp, 520.dp), DpSize(600.dp, 1000.dp))) {
            val views = MonthWidget().compose(context, size = size)
            val bytes = Parcel.obtain().let { parcel ->
                try {
                    views.writeToParcel(parcel, 0)
                    parcel.dataSize()
                } finally {
                    parcel.recycle()
                }
            }
            var dayNumbers = 0
            var bars = 0
            instrumentation.runOnMainSync {
                val root = views.apply(context, FrameLayout(context))
                fun walk(view: View) {
                    if (view.id in DAY_IDS && view is TextView && view.text.isNotEmpty()) dayNumbers += 1
                    if (view.id == R.id.bar_title) bars += 1
                    if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
                walk(root)
            }
            Log.i(TAG, "size=$size bytes=$bytes days=$dayNumbers bars=$bars")
            // The grid itself rendered, not Glance's error layout: a number in every day of every week.
            assertThat(dayNumbers % 7).isEqualTo(0)
            assertThat(dayNumbers).isAtLeast(28)
            assertThat(bytes).isAtMost(MAX_BYTES_PER_SIZE)
        }
    }

    private companion object {
        const val TAG = "MonthWidgetPayload"
        const val ACCOUNT = "calendula-widget-payload-test"
        const val EVENTS_PER_DAY = 6

        val DAY_IDS = setOf(R.id.day_0, R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6)

        /** A quarter of the host's buffer, leaving room for a second size and every other widget. */
        const val MAX_BYTES_PER_SIZE = 256 * 1024
    }
}
