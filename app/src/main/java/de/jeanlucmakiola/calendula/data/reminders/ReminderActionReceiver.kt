package de.jeanlucmakiola.calendula.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.floret.android.goAsync
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Handles the "Snooze" and "Dismiss" actions on a reminder notification, plus
 * the internal re-show when a snooze elapses. All three are app-internal
 * intents (notification action buttons and our own [ReminderSnoozeScheduler]
 * alarm), so the receiver is not exported.
 *
 * - **Dismiss** just cancels the notification — the scan's watermark has moved
 *   past this reminder, so nothing re-posts it.
 * - **Snooze** cancels the notification and schedules an exact alarm to re-show
 *   it after the user's snooze delay.
 * - **Show** (the alarm) re-posts the same notification, so the user can snooze
 *   or dismiss it again — unless the calendar was switched off during the
 *   snooze, which [ReminderNotifier.post] catches — this alarm is its own
 *   trigger, outside the ordinary scan.
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderSnoozeScheduler
    @Inject lateinit var settingsPrefs: SettingsPrefs

    override fun onReceive(context: Context, intent: Intent) {
        val alert = alertFrom(intent) ?: return
        when (intent.action) {
            ACTION_DISMISS -> notifier.cancel(alert)

            ACTION_SNOOZE -> {
                // Tapped on a stale view: the reminder that replaced it stays put.
                if (notifier.isSuperseded(alert)) return
                // Cancel now so the notification doesn't linger until the alarm;
                // the snooze delay read is the only async work.
                notifier.cancel(alert)
                goAsync {
                    val minutes = settingsPrefs.snoozeMinutes.first()
                    val triggerAt = System.currentTimeMillis() + minutes * 60_000L
                    scheduler.schedule(alert, triggerAt)
                }
            }

            ACTION_SHOW -> {
                goAsync {
                    // A later reminder posted while this one was snoozed has taken its place.
                    if (notifier.isSuperseded(alert)) return@goAsync
                    if (settingsPrefs.remindersEnabled.first() && notifier.canPost()) {
                        notifier.post(alert)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "de.jeanlucmakiola.calendula.reminders.SNOOZE"
        const val ACTION_DISMISS = "de.jeanlucmakiola.calendula.reminders.DISMISS"
        const val ACTION_SHOW = "de.jeanlucmakiola.calendula.reminders.SHOW"

        /**
         * Not handled here — the notification body opens the detail screen
         * directly. It only claims a slot in [requestCode] so that intent stays
         * distinct from the three this receiver does handle.
         */
        const val ACTION_OPEN = "de.jeanlucmakiola.calendula.reminders.OPEN"

        private const val EXTRA_ALERT_KEY = "alert_key"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_CALENDAR_ID = "calendar_id"
        private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_END = "end"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_ALL_DAY = "all_day"

        /**
         * An explicit intent to this receiver carrying [alert] as extras. The
         * data URI duplicates no information but is what keeps two reminders'
         * `PendingIntent`s apart — `filterEquals` never compares extras. The
         * snooze alarm is the exception: one per occurrence, so a later snooze
         * replaces an earlier one and a newer reminder can cancel it.
         */
        fun intent(context: Context, action: String, alert: ReminderAlert): Intent =
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                data = "calendula://reminder/${identity(alert, action)}".toUri()
                putExtra(EXTRA_ALERT_KEY, alert.key)
                putExtra(EXTRA_EVENT_ID, alert.eventId)
                putExtra(EXTRA_CALENDAR_ID, alert.calendarId)
                putExtra(EXTRA_BEGIN, alert.beginMillis)
                putExtra(EXTRA_END, alert.endMillis)
                putExtra(EXTRA_TITLE, alert.title)
                putExtra(EXTRA_LOCATION, alert.location)
                putExtra(EXTRA_ALL_DAY, alert.isAllDay)
            }

        /**
         * A stable request code per (alert, action), so one notification's
         * PendingIntents stay distinct. The shift keeps the action slot intact;
         * the top bits it drops are separated by [intent]'s URI.
         */
        fun requestCode(alert: ReminderAlert, action: String): Int {
            val actionOffset = when (action) {
                ACTION_SNOOZE -> 1
                ACTION_DISMISS -> 2
                ACTION_SHOW -> 3
                ACTION_OPEN -> 4
                else -> 0
            }
            return (identity(alert, action).toInt() shl 3) + actionOffset
        }

        private fun identity(alert: ReminderAlert, action: String): Long =
            if (action == ACTION_SHOW) alert.occurrenceKey else alert.key

        private fun alertFrom(intent: Intent): ReminderAlert? {
            if (!intent.hasExtra(EXTRA_ALERT_KEY)) return null
            return ReminderAlert(
                key = intent.getLongExtra(EXTRA_ALERT_KEY, 0L),
                eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L),
                calendarId = intent.getLongExtra(EXTRA_CALENDAR_ID, 0L),
                beginMillis = intent.getLongExtra(EXTRA_BEGIN, 0L),
                endMillis = intent.getLongExtra(EXTRA_END, 0L),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                location = intent.getStringExtra(EXTRA_LOCATION),
                isAllDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false),
            )
        }
    }
}
