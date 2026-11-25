package de.jeanlucmakiola.calendula.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.floret.android.setWakeup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a one-off exact alarm that re-shows a snoozed reminder. Separate
 * from [ReminderAlarmScheduler]'s single moving scan alarm: a snooze is pinned
 * to one reminder and has to outlive the watermark moving past it, so it carries
 * the reminder in its own intent.
 *
 * Falls back to an inexact allow-while-idle alarm where the OS withholds the
 * exact-alarm capability (API 31–32 with the permission revoked).
 */
@Singleton
class ReminderSnoozeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(alert: ReminderAlert, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ReminderActionReceiver.requestCode(alert, ReminderActionReceiver.ACTION_SHOW),
            ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_SHOW, alert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setWakeup(triggerAtMillis, pendingIntent)
    }

    /** Drop a snooze still pending for [alert]'s occurrence, so it can't re-show over a newer reminder. */
    fun cancel(alert: ReminderAlert) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ReminderActionReceiver.requestCode(alert, ReminderActionReceiver.ACTION_SHOW),
            ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_SHOW, alert),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
