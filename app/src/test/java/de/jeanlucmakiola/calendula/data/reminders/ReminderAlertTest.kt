package de.jeanlucmakiola.calendula.data.reminders

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.reminders.PlannedReminder
import de.jeanlucmakiola.calendula.domain.reminders.ReminderEventInstance
import org.junit.jupiter.api.Test

class ReminderAlertTest {

    private fun instance(eventId: Long = 1L, beginMillis: Long = 1_800_000_000_000L) = ReminderEventInstance(
        eventId = eventId,
        calendarId = 1L,
        beginMillis = beginMillis,
        endMillis = beginMillis + 3_600_000L,
        title = "Dentist",
        location = null,
        isAllDay = false,
    )

    private fun alert(instance: ReminderEventInstance, minutes: Int) =
        PlannedReminder(instance, minutes, instance.beginMillis - minutes * 60_000L).toAlert()

    @Test
    fun `two reminders on one occurrence share a notification`() {
        val dayBefore = alert(instance(), 1_440)
        val hourBefore = alert(instance(), 60)

        assertThat(dayBefore.key).isNotEqualTo(hourBefore.key)
        assertThat(dayBefore.occurrenceKey).isEqualTo(hourBefore.occurrenceKey)
    }

    @Test
    fun `occurrences of one series keep their own notifications`() {
        val first = alert(instance(beginMillis = 1_800_000_000_000L), 60)
        val second = alert(instance(beginMillis = 1_800_086_400_000L), 60)

        assertThat(first.occurrenceKey).isNotEqualTo(second.occurrenceKey)
    }
}
