package de.jeanlucmakiola.calendula.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.floret.android.hasPermission
import kotlinx.coroutines.flow.first
import kotlinx.datetime.isoDayNumber
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts one notification per occurrence on a dedicated channel. Tapping opens
 * the event's detail screen.
 *
 * The tag is the [occurrenceKey], so each reminder for an occurrence replaces
 * the one before it in place (#463). A different reminder alerts afresh; the
 * same one posted again — a catch-up pass overlapping the alarm that already
 * fired — replaces its notification silently.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
    private val calendarPrefs: CalendarPrefs,
    private val calendarDataSource: CalendarDataSource,
) {

    /** False when the user declined `POST_NOTIFICATIONS` or muted the app. */
    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * The single choke point for "this calendar is switched off", covering the
     * two paths that reach [post] around the scan's own filter: a snooze armed
     * before the switch-off, and a read-only install whose switch lives in
     * [CalendarPrefs].
     */
    private suspend fun isSilenced(calendarId: Long): Boolean =
        calendarId in calendarPrefs.pendingDisabledCalendarIds.first() ||
            calendarDataSource.isCalendarVisible(calendarId) == false

    /** Post [alert], unless its calendar is switched off. */
    suspend fun post(alert: ReminderAlert) {
        if (isSilenced(alert.calendarId)) return
        ensureChannel()
        val title = displayTitle(alert)
        val is24Hour = settingsPrefs.timeFormat.first()
            .is24Hour(android.text.format.DateFormat.is24HourFormat(context))
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        // resolveFirstDay yields a kotlinx.datetime day; bridge it to java.time by
        // its shared ISO number (1..7) for the date math in reminderTimeText.
        val firstDayOfWeek = DayOfWeek.of(settingsPrefs.weekStart.first().resolveFirstDay(locale).isoDayNumber)
        val time = reminderTimeText(
            beginMillis = alert.beginMillis,
            endMillis = alert.endMillis,
            isAllDay = alert.isAllDay,
            zone = zone,
            locale = locale,
            is24Hour = is24Hour,
            today = Instant.now().atZone(zone).toLocalDate(),
            firstDayOfWeek = firstDayOfWeek,
            todayLabel = context.getString(R.string.reminder_day_today),
            tomorrowLabel = context.getString(R.string.reminder_day_tomorrow),
            yesterdayLabel = context.getString(R.string.reminder_day_yesterday),
        )
        val text = listOfNotNull(time, alert.location).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(alert.beginMillis)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(shownKey(alert) == alert.key)
            .addExtras(Bundle().apply { putLong(EXTRA_REMINDER_KEY, alert.key) })
            .setContentIntent(detailIntent(alert))
            .addAction(
                R.drawable.ic_notification_snooze,
                context.getString(R.string.reminder_action_snooze),
                actionIntent(alert, ReminderActionReceiver.ACTION_SNOOZE),
            )
            .addAction(
                R.drawable.ic_notification_dismiss,
                context.getString(R.string.reminder_action_dismiss),
                actionIntent(alert, ReminderActionReceiver.ACTION_DISMISS),
            )
            .build()
        val manager = NotificationManagerCompat.from(context)
        cancelLegacy(alert)
        try {
            manager.notify(alert.occurrenceKey.toString(), NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between canPost() and here.
            Log.w(TAG, "Could not post reminder for event ${alert.eventId}", e)
        }
    }

    /**
     * Remove [alert]'s notification (snooze re-shows it later; dismiss is final),
     * unless a later reminder has replaced it — an action tapped on a stale view
     * mustn't take the newer one with it.
     */
    fun cancel(alert: ReminderAlert) {
        val manager = NotificationManagerCompat.from(context)
        if (shownKey(alert) == alert.key) manager.cancel(alert.occurrenceKey.toString(), NOTIFICATION_ID)
        // Posted before reminders shared a notification, under the reminder's own key.
        manager.cancel(alert.key.toString(), NOTIFICATION_ID)
    }

    /** Whether a different reminder for [alert]'s occurrence is on screen now. */
    fun isSuperseded(alert: ReminderAlert): Boolean = shownKey(alert).let { it != null && it != alert.key }

    /** The key of the reminder showing for [alert]'s occurrence, if any. */
    private fun shownKey(alert: ReminderAlert): Long? {
        val tag = alert.occurrenceKey.toString()
        return NotificationManagerCompat.from(context).activeNotifications
            .firstOrNull { it.tag == tag && it.id == NOTIFICATION_ID }
            ?.notification?.extras
            ?.takeIf { it.containsKey(EXTRA_REMINDER_KEY) }
            ?.getLong(EXTRA_REMINDER_KEY)
    }

    /**
     * Remove a reminder for the same occurrence posted before reminders shared a
     * notification. It carries no reminder key; its time and title identify it.
     */
    private fun cancelLegacy(alert: ReminderAlert) {
        val manager = NotificationManagerCompat.from(context)
        manager.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.notification.channelId == CHANNEL_ID }
            .filter { !it.notification.extras.containsKey(EXTRA_REMINDER_KEY) }
            .filter { it.notification.`when` == alert.beginMillis }
            .filter { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString() == displayTitle(alert) }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun displayTitle(alert: ReminderAlert): String =
        alert.title.ifBlank { context.getString(R.string.event_untitled) }

    private fun actionIntent(alert: ReminderAlert, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderActionReceiver.requestCode(alert, action),
            ReminderActionReceiver.intent(context, action, alert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun detailIntent(alert: ReminderAlert): PendingIntent = PendingIntent.getActivity(
        context,
        // Shares the per-(alert, action) request-code scheme with the buttons.
        /* requestCode = */ ReminderActionReceiver.requestCode(
            alert, ReminderActionReceiver.ACTION_OPEN,
        ),
        MainActivity.eventDetailIntent(context, alert.eventId, alert.beginMillis, alert.endMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Channel creation is idempotent; re-running refreshes the localized name. */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "ReminderNotifier"
        const val CHANNEL_ID = "reminders"

        // One id, distinct tags: the tag (occurrence) already keys the
        // notification, so a fixed id keeps cancellation/replacement simple.
        const val NOTIFICATION_ID = 1

        const val EXTRA_REMINDER_KEY = "de.jeanlucmakiola.calendula.reminder_key"
    }
}
