package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.parseAgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.storageValue
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.TimelineHours
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.common.parseTimelineScale
import de.jeanlucmakiola.calendula.ui.common.storageValue
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.onboarding.OnboardingStep
import de.jeanlucmakiola.calendula.ui.theme.FONT_SYSTEM_TOKEN
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.prefs.AppearancePrefs
import de.jeanlucmakiola.floret.prefs.Pref
import de.jeanlucmakiola.floret.prefs.PrefStore
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.floret.prefs.booleanPref
import de.jeanlucmakiola.floret.prefs.enumPref
import de.jeanlucmakiola.floret.prefs.get
import de.jeanlucmakiola.floret.prefs.intPref
import de.jeanlucmakiola.floret.prefs.longPref
import de.jeanlucmakiola.floret.prefs.nullableLongPref
import de.jeanlucmakiola.floret.prefs.nullableStringPref
import de.jeanlucmakiola.floret.prefs.set
import de.jeanlucmakiola.floret.prefs.stringPref
import de.jeanlucmakiola.floret.prefs.toEnum
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.ReminderOverrideCodec
import de.jeanlucmakiola.floret.reminders.applyReminderOverride
import de.jeanlucmakiola.floret.reminders.normalizeReminders
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek

/**
 * Week-start override. [Auto] derives the first day from the active locale;
 * [Day] pins a specific weekday (any of the seven).
 */
sealed interface WeekStartPref {
    data object Auto : WeekStartPref
    data class Day(val day: DayOfWeek) : WeekStartPref
}

/**
 * Clock convention for time-of-day labels. AUTO follows the device's 24-hour
 * system setting; the others force a 12- or 24-hour clock app-wide.
 */
enum class TimeFormatPref { AUTO, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/**
 * How the Agenda screen treats events that have already finished today: [SHOW]
 * leaves them as-is (the historical behaviour), [DIM] fades them, [HIDE] drops
 * them from the list entirely.
 */
enum class PastEventDisplay { SHOW, DIM, HIDE }

/** Parse a stored [PastEventDisplay.name]; unknown/null falls back to [default]. */
fun parsePastEventDisplay(
    stored: String?,
    default: PastEventDisplay = PastEventDisplay.SHOW,
): PastEventDisplay = stored.toEnum(default)

/**
 * Resolve to a concrete 24-hour flag. AUTO defers to [systemIs24Hour] (the
 * device's `DateFormat.is24HourFormat` value).
 */
fun TimeFormatPref.is24Hour(systemIs24Hour: Boolean): Boolean = when (this) {
    TimeFormatPref.AUTO -> systemIs24Hour
    TimeFormatPref.TWELVE_HOUR -> false
    TimeFormatPref.TWENTY_FOUR_HOUR -> true
}

/**
 * Resolve the preference to a concrete first-day-of-week. [WeekStartPref.Auto]
 * reads the locale's convention (e.g. Monday in DE, Sunday in en-US).
 */
fun WeekStartPref.resolveFirstDay(locale: Locale): DayOfWeek = when (this) {
    is WeekStartPref.Day -> day
    // java.time.DayOfWeek.value is ISO 1..7 (Mon..Sun) — same numbering kotlinx uses.
    WeekStartPref.Auto -> DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
}

/**
 * The resolved first day of the week as a hot [StateFlow] — the one shape every
 * screen that orders or lays out weekdays should use (the month grid, the agenda
 * "this week" range, the recurrence weekday toggles). Sharing it keeps the
 * initial-frame value identical everywhere; hand-rolled copies had drifted onto
 * different `initialValue`s, so two surfaces could disagree for a frame.
 */
fun SettingsPrefs.firstDayOfWeek(scope: CoroutineScope): StateFlow<DayOfWeek> =
    weekStart
        .map { it.resolveFirstDay(Locale.getDefault()) }
        .stateWhileSubscribed(
            scope = scope,
            // Seed with the locale convention rather than a hardcoded weekday, so
            // the first frame is already right for everyone still on Auto.
            initialValue = WeekStartPref.Auto.resolveFirstDay(Locale.getDefault()),
        )

/**
 * Display, view, editor, reminder, backup and special-dates settings, each a
 * [SettingsKeys] entry read and written through floret's [PrefStore].
 * Language is handled separately through AppCompatDelegate (which persists its
 * own per-app locale).
 *
 * Enum prefs round-trip by [Enum.name]; an unknown/garbage stored value falls
 * back to the default rather than throwing (see SettingsPrefsTest).
 */
@Singleton
class SettingsPrefs @Inject constructor(
    store: DataStore<Preferences>,
) {
    private val prefs = PrefStore(store)

    /** Every setting at once, for screens that read many; index it with [SettingsKeys]. */
    val data: Flow<Preferences> = prefs.data

    val themeMode: Flow<ThemeMode> = prefs.flow(SettingsKeys.themeMode)
    val dynamicColor: Flow<Boolean> = prefs.flow(SettingsKeys.dynamicColor)
    val weekStart: Flow<WeekStartPref> = prefs.flow(SettingsKeys.weekStart)
    val softenCalendarColors: Flow<Boolean> = prefs.flow(SettingsKeys.softenCalendarColors)
    val brandFont: Flow<String> = prefs.flow(SettingsKeys.brandFont)
    val plainFont: Flow<String> = prefs.flow(SettingsKeys.plainFont)
    val brandFontStamp: Flow<Int> = prefs.flow(SettingsKeys.brandFontStamp)
    val plainFontStamp: Flow<Int> = prefs.flow(SettingsKeys.plainFontStamp)
    val timeFormat: Flow<TimeFormatPref> = prefs.flow(SettingsKeys.timeFormat)
    val showHourGrid: Flow<Boolean> = prefs.flow(SettingsKeys.showHourGrid)
    val pastEventDisplay: Flow<PastEventDisplay> = prefs.flow(SettingsKeys.pastEventDisplay)
    val dimCompletedEvents: Flow<Boolean> = prefs.flow(SettingsKeys.dimCompletedEvents)
    val showWeekNumbers: Flow<Boolean> = prefs.flow(SettingsKeys.showWeekNumbers)
    val monthShowStartTimes: Flow<Boolean> = prefs.flow(SettingsKeys.monthShowStartTimes)
    val monthViewStyle: Flow<MonthViewStyle> = prefs.flow(SettingsKeys.monthViewStyle)
    val timelineScale: Flow<TimelineScale> = prefs.flow(SettingsKeys.timelineScale)
    val multiDayCount: Flow<Int> = prefs.flow(SettingsKeys.multiDayCount)
    val timelineHours: Flow<TimelineHours> = prefs.snapshot { it.timelineHours }
    val todayButtonInToolbar: Flow<Boolean> = prefs.flow(SettingsKeys.todayButtonInToolbar)
    val dragToReschedule: Flow<Boolean> = prefs.flow(SettingsKeys.dragToReschedule)
    val tapOpensEditor: Flow<Boolean> = prefs.flow(SettingsKeys.tapOpensEditor)
    val agendaScreenRange: Flow<AgendaRange> = prefs.flow(SettingsKeys.agendaScreenRange)
    val agendaShowRangeBar: Flow<Boolean> = prefs.flow(SettingsKeys.agendaShowRangeBar)
    val agendaShowToday: Flow<Boolean> = prefs.flow(SettingsKeys.agendaShowToday)
    val defaultView: Flow<CalendarView> = prefs.flow(SettingsKeys.defaultView)
    val quickSwitchConfig: Flow<QuickSwitchConfig> = prefs.flow(SettingsKeys.quickSwitchConfig)
    val drawerViewOrder: Flow<List<CalendarView>> = prefs.flow(SettingsKeys.drawerViewOrder)
    val defaultFormFields: Flow<Set<EventFormField>> = prefs.flow(SettingsKeys.defaultFormFields)
    val recentTimeZones: Flow<List<String>> = prefs.flow(SettingsKeys.recentTimeZones)
    val autofocusEventTitle: Flow<Boolean> = prefs.flow(SettingsKeys.autofocusEventTitle)
    val defaultEventDurationMinutes: Flow<Int> = prefs.flow(SettingsKeys.defaultEventDurationMinutes)
    val perCalendarEventDuration: Flow<Map<Long, Int>> = prefs.flow(SettingsKeys.perCalendarEventDuration)
    val remindersEnabled: Flow<Boolean> = prefs.flow(SettingsKeys.remindersEnabled)
    val allowColorOnUnsupportedCalendars: Flow<Boolean> =
        prefs.flow(SettingsKeys.allowColorOnUnsupportedCalendars)
    val defaultReminderMinutes: Flow<List<Int>> = prefs.flow(SettingsKeys.defaultReminderMinutes)
    val defaultAllDayReminderMinutes: Flow<List<Int>> = prefs.flow(SettingsKeys.defaultAllDayReminderMinutes)
    val allDayReminderTimeMinutes: Flow<Int> = prefs.flow(SettingsKeys.allDayReminderTimeMinutes)
    val snoozeMinutes: Flow<Int> = prefs.flow(SettingsKeys.snoozeMinutes)
    val perCalendarReminderOverride: Flow<Map<Long, List<Int>>> =
        prefs.flow(SettingsKeys.perCalendarReminderOverride)
    val perCalendarAllDayReminderOverride: Flow<Map<Long, List<Int>>> =
        prefs.flow(SettingsKeys.perCalendarAllDayReminderOverride)
    val autoBackupEnabled: Flow<Boolean> = prefs.flow(SettingsKeys.autoBackupEnabled)
    val autoBackupIntervalMinutes: Flow<Long> = prefs.flow(SettingsKeys.autoBackupIntervalMinutes)
    val autoBackupFolderUri: Flow<String?> = prefs.flow(SettingsKeys.autoBackupFolderUri)
    val autoBackupStatus: Flow<BackupStatus> = prefs.snapshot { it.autoBackupStatus }
    val specialDatesEnabled: Flow<Boolean> = prefs.flow(SettingsKeys.specialDatesEnabled)
    val specialDatesTypes: Flow<Set<SpecialDateType>> = prefs.snapshot { it.specialDatesTypes }
    val specialDatesCalendars: Flow<Map<SpecialDateType, Long>> = prefs.snapshot { it.specialDatesCalendars }

    /** Every managed calendar id — the editor locks title/date/recurrence for their events. */
    val managedCalendarIds: Flow<Set<Long>> = prefs.snapshot { it.specialDatesCalendars.values.toSet() }
    val specialDatesTitleTemplates: Flow<Map<SpecialDateType, String>> =
        prefs.snapshot { it.specialDatesTitleTemplates }
    val specialDatesShowYear: Flow<Boolean> = prefs.flow(SettingsKeys.specialDatesShowYear)
    val specialDatesStatus: Flow<SpecialDatesStatus> = prefs.snapshot { it.specialDatesStatus }
    val specialDatesLastForegroundSync: Flow<Long> = prefs.flow(SettingsKeys.specialDatesLastForegroundSync)

    suspend fun setThemeMode(mode: ThemeMode) = prefs.set(SettingsKeys.themeMode, mode)
    suspend fun setDynamicColor(enabled: Boolean) = prefs.set(SettingsKeys.dynamicColor, enabled)
    suspend fun setWeekStart(pref: WeekStartPref) = prefs.set(SettingsKeys.weekStart, pref)
    suspend fun setSoftenCalendarColors(enabled: Boolean) = prefs.set(SettingsKeys.softenCalendarColors, enabled)
    suspend fun setBrandFont(token: String) = prefs.set(SettingsKeys.brandFont, token)
    suspend fun setPlainFont(token: String) = prefs.set(SettingsKeys.plainFont, token)

    /** Bump [role]'s custom-font stamp after a re-import so equality-keyed caches refresh. */
    suspend fun bumpCustomFontStamp(role: FontRole) {
        val stamp = when (role) {
            FontRole.BRAND -> SettingsKeys.brandFontStamp
            FontRole.PLAIN -> SettingsKeys.plainFontStamp
        }
        prefs.update(stamp) { it + 1 }
    }

    suspend fun setTimeFormat(pref: TimeFormatPref) = prefs.set(SettingsKeys.timeFormat, pref)
    suspend fun setShowHourGrid(enabled: Boolean) = prefs.set(SettingsKeys.showHourGrid, enabled)
    suspend fun setPastEventDisplay(mode: PastEventDisplay) = prefs.set(SettingsKeys.pastEventDisplay, mode)
    suspend fun setDimCompletedEvents(enabled: Boolean) = prefs.set(SettingsKeys.dimCompletedEvents, enabled)
    suspend fun setShowWeekNumbers(enabled: Boolean) = prefs.set(SettingsKeys.showWeekNumbers, enabled)
    suspend fun setMonthShowStartTimes(enabled: Boolean) = prefs.set(SettingsKeys.monthShowStartTimes, enabled)
    suspend fun setMonthViewStyle(style: MonthViewStyle) = prefs.set(SettingsKeys.monthViewStyle, style)
    suspend fun setTimelineScale(scale: TimelineScale) = prefs.set(SettingsKeys.timelineScale, scale)
    suspend fun setMultiDayCount(count: Int) = prefs.set(SettingsKeys.multiDayCount, count)

    suspend fun setTimelineHours(hours: TimelineHours) {
        val valid = TimelineHours.of(hours.enabled, hours.startHour, hours.endHour)
        prefs.edit {
            it[SettingsKeys.timelineHoursEnabled] = valid.enabled
            it[SettingsKeys.timelineHoursStart] = valid.startHour
            it[SettingsKeys.timelineHoursEnd] = valid.endHour
        }
    }

    suspend fun setTodayButtonInToolbar(enabled: Boolean) = prefs.set(SettingsKeys.todayButtonInToolbar, enabled)
    suspend fun setDragToReschedule(enabled: Boolean) = prefs.set(SettingsKeys.dragToReschedule, enabled)
    suspend fun setTapOpensEditor(enabled: Boolean) = prefs.set(SettingsKeys.tapOpensEditor, enabled)
    suspend fun setAgendaScreenRange(range: AgendaRange) = prefs.set(SettingsKeys.agendaScreenRange, range)
    suspend fun setAgendaShowRangeBar(enabled: Boolean) = prefs.set(SettingsKeys.agendaShowRangeBar, enabled)
    suspend fun setAgendaShowToday(enabled: Boolean) = prefs.set(SettingsKeys.agendaShowToday, enabled)
    suspend fun setDefaultView(view: CalendarView) = prefs.set(SettingsKeys.defaultView, view)

    /**
     * Atomic read-modify-write of the quick-switch config: [transform] sees the
     * value currently stored, parsed inside the edit block, not the async-echoed
     * UI snapshot. Two rapid intents (a toggle then a drag) therefore compose
     * instead of each re-serialising a stale copy of the other field over it.
     */
    suspend fun updateQuickSwitch(transform: (QuickSwitchConfig) -> QuickSwitchConfig) {
        prefs.update(SettingsKeys.quickSwitchConfig, transform)
    }

    suspend fun setDrawerViewOrder(order: List<CalendarView>) = prefs.set(SettingsKeys.drawerViewOrder, order)

    suspend fun setFormFieldDefault(field: EventFormField, enabled: Boolean) {
        prefs.update(SettingsKeys.defaultFormFields) { if (enabled) it + field else it - field }
    }

    suspend fun addRecentTimeZone(zoneId: String) {
        prefs.update(SettingsKeys.recentTimeZones) { (listOf(zoneId) + it).distinct() }
    }

    suspend fun setAutofocusEventTitle(enabled: Boolean) = prefs.set(SettingsKeys.autofocusEventTitle, enabled)

    suspend fun setDefaultEventDurationMinutes(minutes: Int) =
        prefs.set(SettingsKeys.defaultEventDurationMinutes, minutes)

    /** [minutes] null drops the override, so the calendar inherits the global default. */
    suspend fun setCalendarEventDuration(calendarId: Long, minutes: Int?) {
        prefs.update(SettingsKeys.perCalendarEventDuration) { current ->
            if (minutes == null) {
                current - calendarId
            } else {
                current + (calendarId to minutes.coerceIn(MIN_EVENT_DURATION, MAX_EVENT_DURATION))
            }
        }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) = prefs.set(SettingsKeys.remindersEnabled, enabled)

    suspend fun setAllowColorOnUnsupportedCalendars(enabled: Boolean) =
        prefs.set(SettingsKeys.allowColorOnUnsupportedCalendars, enabled)

    /** Which wizard steps have been answered (or skipped), and whether the wizard is armed. */
    val onboardingProgress: Flow<OnboardingProgress> = prefs.snapshot { it.onboardingProgress }

    /** Cleared, not just set: the wizard's back button re-asks a step (#163). */
    suspend fun setOnboardingDone(step: OnboardingStep, done: Boolean = true) {
        val flag = SettingsKeys.onboardingDone[step] ?: return
        prefs.set(flag, done)
    }

    /**
     * Arm the wizard's optional steps for an install that has not finished the
     * reminder step (#163); see [SettingsKeys.onboardingWizardArmed].
     */
    suspend fun armOnboardingWizard() {
        prefs.edit {
            if (!it[SettingsKeys.reminderOnboardingDone]) it[SettingsKeys.onboardingWizardArmed] = true
        }
    }

    /**
     * Close the wizard for good: the closing screen has been seen and the
     * install stops counting as fresh, so a later revoke-and-re-grant is only
     * the permission screen. One edit, so the plan can't see it half-closed.
     */
    suspend fun finishOnboardingWizard() {
        prefs.edit {
            it[SettingsKeys.onboardingDoneShown] = true
            it[SettingsKeys.onboardingWizardArmed] = false
        }
    }

    suspend fun setDefaultReminderMinutes(minutes: List<Int>) =
        prefs.set(SettingsKeys.defaultReminderMinutes, minutes)

    suspend fun setDefaultAllDayReminderMinutes(minutes: List<Int>) =
        prefs.set(SettingsKeys.defaultAllDayReminderMinutes, minutes)

    suspend fun setAllDayReminderTimeMinutes(minutesOfDay: Int) =
        prefs.set(SettingsKeys.allDayReminderTimeMinutes, minutesOfDay)

    suspend fun setSnoozeMinutes(minutes: Int) = prefs.set(SettingsKeys.snoozeMinutes, minutes)

    suspend fun setCalendarReminderOverride(calendarId: Long, override: ReminderOverride) =
        prefs.applyOverride(SettingsKeys.perCalendarReminderOverride, calendarId, override)

    suspend fun setCalendarAllDayReminderOverride(calendarId: Long, override: ReminderOverride) =
        prefs.applyOverride(SettingsKeys.perCalendarAllDayReminderOverride, calendarId, override)

    suspend fun setAutoBackupEnabled(enabled: Boolean) = prefs.set(SettingsKeys.autoBackupEnabled, enabled)

    suspend fun setAutoBackupIntervalMinutes(minutes: Long) =
        prefs.set(SettingsKeys.autoBackupIntervalMinutes, minutes)

    suspend fun setAutoBackupFolderUri(uri: String?) = prefs.set(SettingsKeys.autoBackupFolderUri, uri)

    /** Record an automatic run's outcome; resets the failure streak on success. */
    suspend fun recordAutoBackupRun(success: Boolean, atMillis: Long) {
        prefs.edit {
            it[SettingsKeys.autoBackupLastRun] = atMillis
            it[SettingsKeys.autoBackupLastSuccess] = success
            it[SettingsKeys.autoBackupFailures] = if (success) 0 else it[SettingsKeys.autoBackupFailures] + 1
        }
    }

    suspend fun setSpecialDatesEnabled(enabled: Boolean) = prefs.set(SettingsKeys.specialDatesEnabled, enabled)

    suspend fun setSpecialDateTypeEnabled(type: SpecialDateType, enabled: Boolean) =
        prefs.set(SettingsKeys.specialDateTypeEnabled.getValue(type), enabled)

    suspend fun setSpecialDatesCalendarId(type: SpecialDateType, calendarId: Long?) =
        prefs.set(SettingsKeys.specialDatesCalendarId.getValue(type), calendarId)

    suspend fun setSpecialDatesTitleTemplate(type: SpecialDateType, template: String) =
        prefs.set(SettingsKeys.specialDatesTitleTemplate.getValue(type), template)

    suspend fun setSpecialDatesShowYear(enabled: Boolean) = prefs.set(SettingsKeys.specialDatesShowYear, enabled)

    /** Record a sync outcome; [stalled] non-null marks the feature paused (e.g. permission revoked). */
    suspend fun recordSpecialDatesRun(atMillis: Long, stalled: SpecialDatesStalledReason?) {
        prefs.edit {
            it[SettingsKeys.specialDatesLastRun] = atMillis
            it[SettingsKeys.specialDatesStalled] = stalled
        }
    }

    suspend fun setSpecialDatesLastForegroundSync(atMillis: Long) =
        prefs.set(SettingsKeys.specialDatesLastForegroundSync, atMillis)

    companion object {
        /** A new timed event's length until the user changes it: one hour. */
        const val DEFAULT_EVENT_DURATION = 60
        internal const val MIN_EVENT_DURATION = 1
        /** A day — past that the form is describing a multi-day event, not a default. */
        internal const val MAX_EVENT_DURATION = 1_440
        /** 09:00 as minutes from midnight; the default all-day reminder fire time. */
        internal const val DEFAULT_ALLDAY_REMINDER_TIME = 540
        internal const val MINUTES_PER_DAY = 1_440
        /** Default snooze delay for the notification "Snooze" action. */
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_MULTI_DAY_COUNT = 3
        val MULTI_DAY_COUNTS = 3..6
        internal val DEFAULT_FORM_FIELDS =
            setOf(EventFormField.Location, EventFormField.Description)
        /** Enough to cover the zones a user actually recurs to, without a wall of rows. */
        internal const val MAX_RECENT_TIME_ZONES = 5
        /** Default automatic-backup interval: daily. */
        const val DEFAULT_BACKUP_INTERVAL = 1_440L
        /** Floor for the automatic-backup interval (also above WorkManager's 15-min limit). */
        const val MIN_BACKUP_INTERVAL = 30L
    }
}

/**
 * Every setting [SettingsPrefs] stores. The names are on-disk data, fixed at the
 * release that introduced each one; renaming one is a migration.
 */
object SettingsKeys {
    val themeMode: Pref<ThemeMode> = AppearancePrefs.themeMode
    val dynamicColor: Pref<Boolean> = AppearancePrefs.dynamicColor

    /** "AUTO" (or unset) follows the locale; a [DayOfWeek] name pins that day. */
    val weekStart: Pref<WeekStartPref> =
        nullableStringPref("week_start").map(::parseWeekStart) { it.storageValue() }

    /**
     * Whether raw provider colours are softened toward theme-fitting pastels
     * before display (issue #36). Defaults to ON — the historical look. Turning
     * it off paints the calendar/event colour exactly as the sync source
     * (DAVx5/CalDAV) publishes it.
     */
    val softenCalendarColors: Pref<Boolean> = booleanPref("soften_calendar_colors", true)

    /**
     * Custom-font tokens per Material typeface role (issue #19): "system",
     * "custom", or a bundled font's token, resolved to a FontFamily at the theme
     * layer; an unknown token degrades to the default.
     */
    val brandFont: Pref<String> = stringPref("brand_font", FONT_SYSTEM_TOKEN)
    val plainFont: Pref<String> = stringPref("plain_font", FONT_SYSTEM_TOKEN)

    /**
     * A per-role bump counter for the user-loaded custom font. Re-importing
     * overwrites the same file under the same "custom" token, so the token alone
     * can't signal the change; this stamp — carried into AppFontSettings — breaks
     * value equality so the resolved FontFamily (and its previews) refresh.
     */
    val brandFontStamp: Pref<Int> = intPref("brand_font_stamp", 0)
    val plainFontStamp: Pref<Int> = intPref("plain_font_stamp", 0)

    /** Clock convention for time-of-day labels (v2.11). */
    val timeFormat: Pref<TimeFormatPref> = enumPref("time_format", TimeFormatPref.AUTO)

    /**
     * Whether the week/day timeline seats each hour in its own cell (v2.11, a
     * separator line until v2.20). The stored key keeps its original name.
     */
    val showHourGrid: Pref<Boolean> = booleanPref("show_hour_lines", false)

    /** How the Agenda screen treats events that already ended today. */
    val pastEventDisplay: Pref<PastEventDisplay> =
        enumPref("agenda_past_event_display", PastEventDisplay.SHOW)

    /** Whether the month/week grids fade finished events; independent of [pastEventDisplay]. */
    val dimCompletedEvents: Pref<Boolean> = booleanPref("dim_completed_events", false)

    /**
     * Whether the Month grid shows the ISO week number in a left gutter (#25).
     * The Week view shows its number unconditionally.
     */
    val showWeekNumbers: Pref<Boolean> = booleanPref("show_week_numbers", false)

    /** Whether Month chips may show the start time before the title when wide enough (#219, #476). */
    val monthShowStartTimes: Pref<Boolean> = booleanPref("month_show_start_times", true)

    /** How the Month view lays itself out (#38, #53). */
    val monthViewStyle: Pref<MonthViewStyle> = enumPref("month_view_style", MonthViewStyle.Paged)

    /**
     * How tall an hour is drawn in the week and day timelines (#56): a preset or
     * the height a pinch on the timeline settled at.
     */
    val timelineScale: Pref<TimelineScale> =
        nullableStringPref("timeline_scale").map(::parseTimelineScale) { it.storageValue() }

    /** How many days the multi-day view shows side by side (#193). */
    val multiDayCount: Pref<Int> =
        intPref("multi_day_count", SettingsPrefs.DEFAULT_MULTI_DAY_COUNT).coerced {
            it.coerceIn(SettingsPrefs.MULTI_DAY_COUNTS)
        }

    /**
     * The hours the day, week and multi-day timelines fit to the screen when they
     * open (#322). Off by default; the hours survive switching it off. Read
     * together as [Preferences.timelineHours].
     */
    val timelineHoursEnabled: Pref<Boolean> = booleanPref("timeline_hours_enabled", false)
    val timelineHoursStart: Pref<Int> = intPref("timeline_hours_start", TimelineHours.DEFAULT_START_HOUR)
    val timelineHoursEnd: Pref<Int> = intPref("timeline_hours_end", TimelineHours.DEFAULT_END_HOUR)

    /**
     * Where the jump-to-today control lives (issue #60). Off is an extended FAB
     * above the "+" while the view is off today; on is a persistent icon button
     * in each calendar view's top bar.
     */
    val todayButtonInToolbar: Pref<Boolean> = booleanPref("today_button_in_toolbar", false)

    /**
     * Whether events can be dragged to another slot in the calendar views (#68,
     * #173). Off means no block registers a drag gesture at all.
     */
    val dragToReschedule: Pref<Boolean> = booleanPref("drag_to_reschedule", true)

    /**
     * Whether tapping an event in the calendar views opens the edit form instead
     * of the detail screen (#178). Read-only events and reminder notifications
     * always open the detail screen.
     */
    val tapOpensEditor: Pref<Boolean> = booleanPref("tap_opens_editor", false)

    /** How far ahead the in-app Agenda screen shows events (v2.11). */
    val agendaScreenRange: Pref<AgendaRange> = agendaRangePref("agenda_screen_range")

    /**
     * How far ahead the agenda **widget** shows events (v2.11). Read-only since
     * widgets keep their own options (#292): the default for a widget that has
     * none yet.
     */
    val agendaWidgetRange: Pref<AgendaRange> = agendaRangePref("agenda_widget_range")

    /**
     * The size step the agenda widget draws its text at (#51). [WidgetSize.SMALL]
     * reproduces its original metrics. Read-only like [agendaWidgetRange] (#292).
     */
    val widgetSize: Pref<WidgetSize> = enumPref("widget_size", WidgetSize.SMALL)

    /** Whether the agenda shows its "showing …" header and range switcher (v2.11). */
    val agendaShowRangeBar: Pref<Boolean> = booleanPref("agenda_show_range_bar", true)

    /**
     * Whether the agenda screen always anchors today at the top with a "nothing
     * left today" placeholder (issue #35), and the default for a widget (#292).
     */
    val agendaShowToday: Pref<Boolean> = booleanPref("agenda_show_today", true)

    /**
     * The calendar view the app opens on (M1), and the bottom of the in-app view
     * back stack. [CalendarView.Week] was the historical hard-coded startup view.
     */
    val defaultView: Pref<CalendarView> = enumPref("default_view", CalendarView.Week)

    /**
     * Quick-switch button customisation (#24): the ordered full view list plus
     * which views are enabled in the cycle. Stored comma-joined by enum name, a
     * "!" prefix marking a disabled view (e.g. "Month,Week,!Day,Agenda"). Missing
     * views are appended enabled and unknown names dropped, so a future view
     * defaults into the cycle.
     */
    val quickSwitchConfig: Pref<QuickSwitchConfig> =
        nullableStringPref("quick_switch_views").map(::parseQuickSwitch, ::serializeQuickSwitch)

    /**
     * Navigation-drawer view order (#24), comma-joined enum names; missing views
     * are appended in default order and unknown names dropped.
     */
    val drawerViewOrder: Pref<List<CalendarView>> =
        nullableStringPref("drawer_view_order").map(::parseViewOrder) { order -> order.joinToString(",") { it.name } }

    /**
     * Optional event-form fields shown by default (the rest hide behind "more
     * fields"), comma-joined by enum name: an absent key means the factory
     * default, an empty string means "none".
     */
    val defaultFormFields: Pref<Set<EventFormField>> =
        nullableStringPref("event_form_default_fields").map(::parseFormFields) { fields ->
            fields.joinToString(",") { it.name }
        }

    /**
     * Zones the user picked recently, most recent first — the timezone picker's
     * default list. Stored comma-joined (IANA ids never contain a comma);
     * unparseable ids are dropped on read, so a zone the tz database later
     * retires can't wedge the list.
     */
    val recentTimeZones: Pref<List<String>> =
        nullableStringPref("recent_time_zones").map(::parseRecentTimeZones) { zones ->
            zones.take(SettingsPrefs.MAX_RECENT_TIME_ZONES).joinToString(",")
        }

    /**
     * Whether opening the new-event form focuses the title field and raises the
     * keyboard straight away (issue #10). Only the create form auto-focuses.
     */
    val autofocusEventTitle: Pref<Boolean> = booleanPref("autofocus_event_title", true)

    /**
     * How long a new **timed** event lasts, in minutes (#54). Per-calendar
     * overrides in [perCalendarEventDuration] take precedence; all-day events
     * ignore it. Resolve with [resolveDefaultEventDuration].
     */
    val defaultEventDurationMinutes: Pref<Int> =
        intPref("default_event_duration_minutes", SettingsPrefs.DEFAULT_EVENT_DURATION).coerced {
            it.coerceIn(SettingsPrefs.MIN_EVENT_DURATION, SettingsPrefs.MAX_EVENT_DURATION)
        }

    /**
     * Per-calendar overrides of [defaultEventDurationMinutes]: a calendar present
     * in the map gives its new events that length, one absent inherits the
     * global default. Serialised as `id=minutes;id=minutes`.
     */
    val perCalendarEventDuration: Pref<Map<Long, Int>> =
        nullableStringPref("per_calendar_event_duration").map(::parseDurationOverrides) { it.toStoredDurations() }

    /**
     * Whether Calendula posts reminder notifications (v1.4). On by default — for
     * users whose only calendar app this is, reminders are essential.
     */
    val remindersEnabled: Pref<Boolean> = booleanPref("reminders_enabled", true)

    /**
     * Whether to offer a custom event colour on calendars that publish no colour
     * palette; synced ones without a palette (some CalDAV) may drop or overwrite
     * a raw colour on their next sync. Local and palette-backed calendars are
     * unaffected.
     */
    val allowColorOnUnsupportedCalendars: Pref<Boolean> =
        booleanPref("allow_color_unsupported_calendars", false)

    /** Whether the reminder onboarding step has been shown — also true after "not now". */
    val reminderOnboardingDone: Pref<Boolean> = booleanPref("reminder_onboarding_done", false)

    /**
     * Whether this install is a fresh one still owing the wizard's optional
     * steps (#163). Armed on any launch that holds the calendar permission and
     * has not finished the reminder step, and cleared again by
     * [SettingsPrefs.finishOnboardingWizard], so an existing user who revokes and
     * re-grants the permission isn't re-onboarded.
     */
    val onboardingWizardArmed: Pref<Boolean> = booleanPref("onboarding_wizard_armed", false)

    /** Whether the wizard's closing screen has been seen. */
    val onboardingDoneShown: Pref<Boolean> = booleanPref("onboarding_done_shown", false)

    /**
     * Whether each wizard step has been answered (or skipped). The visibility
     * flag is separate from [CalendarPrefs.visibilityNoticePending], which says
     * whether this install has anything to announce at all.
     */
    val onboardingDone: Map<OnboardingStep, Pref<Boolean>> = mapOf(
        OnboardingStep.HiddenCalendars to booleanPref("onboarding_hidden_calendars_done", false),
        OnboardingStep.Calendars to booleanPref("onboarding_calendars_done", false),
        OnboardingStep.Reminders to reminderOnboardingDone,
        OnboardingStep.Backup to booleanPref("onboarding_backup_done", false),
        OnboardingStep.View to booleanPref("onboarding_view_done", false),
        OnboardingStep.MonthStyle to booleanPref("onboarding_month_style_done", false),
        OnboardingStep.Visibility to booleanPref("onboarding_visibility_done", false),
        OnboardingStep.Done to onboardingDoneShown,
    )

    /**
     * The default reminder lead times prefilled on new **timed** events; empty
     * means none, the factory default. Stored comma-joined, or "none"/empty for
     * no reminder; a legacy single value ("30") parses to a one-element list.
     * Per-calendar overrides in [perCalendarReminderOverride] take precedence.
     * Resolve with [resolveDefaultReminder].
     */
    val defaultReminderMinutes: Pref<List<Int>> = reminderListPref("default_reminder_minutes")

    /**
     * The default lead times prefilled on new **all-day** events, in minutes
     * before the start of the day — day-scale ("1 day before"), so separate from
     * the timed default.
     */
    val defaultAllDayReminderMinutes: Pref<List<Int>> = reminderListPref("default_allday_reminder_minutes")

    /**
     * Wall-clock time, as minutes from local midnight, at which **all-day**
     * reminders fire. All-day events live at UTC midnight, so a raw "1 day
     * before" would fire at an off hour (02:00 local in CEST); this time is
     * encoded into the provider offset so the reminder lands at, e.g., 09:00 the
     * day before instead.
     */
    val allDayReminderTimeMinutes: Pref<Int> =
        intPref("allday_reminder_time_minutes", SettingsPrefs.DEFAULT_ALLDAY_REMINDER_TIME).coerced {
            it.coerceIn(0, SettingsPrefs.MINUTES_PER_DAY - 1)
        }

    /**
     * How long the notification "Snooze" action defers a reminder, in minutes,
     * re-posted via an exact alarm (the calendar provider won't re-fire).
     */
    val snoozeMinutes: Pref<Int> =
        intPref("snooze_minutes", SettingsPrefs.DEFAULT_SNOOZE_MINUTES).coerced { it.coerceAtLeast(1) }

    /**
     * Per-calendar overrides of [defaultReminderMinutes] for **timed** events. A
     * calendar present in the map overrides the global default: an empty list
     * means "no reminder". A calendar absent inherits the global default.
     * Serialised as `id=value;id=value`, a value being a comma-joined minute list
     * or `none`.
     */
    val perCalendarReminderOverride: Pref<Map<Long, List<Int>>> =
        reminderOverridePref("per_calendar_reminder_override")

    /** Per-calendar overrides of [defaultAllDayReminderMinutes], as [perCalendarReminderOverride]. */
    val perCalendarAllDayReminderOverride: Pref<Map<Long, List<Int>>> =
        reminderOverridePref("per_calendar_allday_reminder_override")

    /** Whether periodic automatic `.ics` export of local calendars is on (issue #8). */
    val autoBackupEnabled: Pref<Boolean> = booleanPref("auto_backup_enabled", false)

    /** Export interval in minutes, floored at [SettingsPrefs.MIN_BACKUP_INTERVAL]. */
    val autoBackupIntervalMinutes: Pref<Long> =
        longPref("auto_backup_interval_minutes", SettingsPrefs.DEFAULT_BACKUP_INTERVAL).coerced {
            it.coerceAtLeast(SettingsPrefs.MIN_BACKUP_INTERVAL)
        }

    /** Persisted SAF tree Uri of the destination folder, or null if unset. */
    val autoBackupFolderUri: Pref<String?> = nullableStringPref("auto_backup_folder_uri")

    /** The last automatic run's outcome, read together as [Preferences.autoBackupStatus]. */
    val autoBackupLastRun: Pref<Long> = longPref("auto_backup_last_run", 0L)
    val autoBackupLastSuccess: Pref<Boolean> = booleanPref("auto_backup_last_success", true)
    val autoBackupFailures: Pref<Int> = intPref("auto_backup_failures", 0)

    /** Master switch for the contact special-dates mirror (issue #15). Opt-in. */
    val specialDatesEnabled: Pref<Boolean> = booleanPref("special_dates_enabled", false)

    /** The date types the user wants mirrored (default: all three). */
    val specialDateTypeEnabled: Map<SpecialDateType, Pref<Boolean>> =
        SpecialDateType.entries.associateWith { booleanPref("special_dates_type_${it.name}", true) }

    /** The managed calendar id per type, for the ones that currently exist. */
    val specialDatesCalendarId: Map<SpecialDateType, Pref<Long?>> =
        SpecialDateType.entries.associateWith { nullableLongPref("special_dates_calendar_${it.name}") }

    /**
     * The title template per type ("{name}'s birthday"); an empty string means
     * unset, so the settings screen can seed the localized default. `{name}` is
     * the contact name, `{age}` the age at the upcoming date (empty when the
     * birth year is unknown or [specialDatesShowYear] is off).
     */
    val specialDatesTitleTemplate: Map<SpecialDateType, Pref<String>> =
        SpecialDateType.entries.associateWith { stringPref("special_dates_title_${it.name}", "") }

    /** Whether `{year}` resolves in the title (only meaningful when the year is known). */
    val specialDatesShowYear: Pref<Boolean> = booleanPref("special_dates_show_year", true)

    /** The last mirror sync's outcome, read together as [Preferences.specialDatesStatus]. */
    val specialDatesLastRun: Pref<Long> = longPref("special_dates_last_run", 0L)
    val specialDatesStalled: Pref<SpecialDatesStalledReason?> =
        nullableStringPref("special_dates_stalled_reason").map(
            { stored -> stored?.let { name -> SpecialDatesStalledReason.entries.firstOrNull { it.name == name } } },
            { it?.name },
        )

    /** Epoch millis of the last foreground-triggered sync, to debounce ON_RESUME. */
    val specialDatesLastForegroundSync: Pref<Long> = longPref("special_dates_last_foreground_sync", 0L)
}

val Preferences.timelineHours: TimelineHours
    get() = TimelineHours.of(
        enabled = this[SettingsKeys.timelineHoursEnabled],
        startHour = this[SettingsKeys.timelineHoursStart],
        endHour = this[SettingsKeys.timelineHoursEnd],
    )

val Preferences.autoBackupStatus: BackupStatus
    get() = BackupStatus(
        lastRun = this[SettingsKeys.autoBackupLastRun],
        lastSuccess = this[SettingsKeys.autoBackupLastSuccess],
        consecutiveFailures = this[SettingsKeys.autoBackupFailures],
    )

val Preferences.specialDatesTypes: Set<SpecialDateType>
    get() = SettingsKeys.specialDateTypeEnabled.filterValues { this[it] }.keys

val Preferences.specialDatesCalendars: Map<SpecialDateType, Long>
    get() = SettingsKeys.specialDatesCalendarId.mapNotNull { (type, pref) -> this[pref]?.let { type to it } }.toMap()

val Preferences.specialDatesTitleTemplates: Map<SpecialDateType, String>
    get() = SettingsKeys.specialDatesTitleTemplate.mapValues { (_, pref) -> this[pref] }

val Preferences.specialDatesStatus: SpecialDatesStatus
    get() = SpecialDatesStatus(
        lastRun = this[SettingsKeys.specialDatesLastRun],
        stalled = this[SettingsKeys.specialDatesStalled],
    )

val Preferences.onboardingProgress: OnboardingProgress
    get() = OnboardingProgress(
        answered = SettingsKeys.onboardingDone.filterValues { this[it] }.keys,
        wizardArmed = this[SettingsKeys.onboardingWizardArmed],
    )

/** The wizard's stored answers (#163); see [SettingsKeys.onboardingDone]. */
data class OnboardingProgress(
    val answered: Set<OnboardingStep>,
    val wizardArmed: Boolean,
)

private fun <T> PrefStore.snapshot(read: (Preferences) -> T): Flow<T> =
    data.map(read).distinctUntilChanged()

/** Clamps on both read and write, so an out-of-range stored value degrades instead of leaking. */
private fun <T> Pref<T>.coerced(clamp: (T) -> T): Pref<T> = map(clamp, clamp)

private fun agendaRangePref(name: String): Pref<AgendaRange> =
    nullableStringPref(name).map({ parseAgendaRange(it, AgendaRange.Month) }, { it.storageValue() })

private fun reminderListPref(name: String): Pref<List<Int>> =
    nullableStringPref(name).map({ it.toReminderList() }, { it.toStoredReminders() })

private fun reminderOverridePref(name: String): Pref<Map<Long, List<Int>>> =
    nullableStringPref(name).map(reminderOverrideCodec::parse, reminderOverrideCodec::serialize)

private suspend fun PrefStore.applyOverride(
    pref: Pref<Map<Long, List<Int>>>,
    calendarId: Long,
    override: ReminderOverride,
) {
    update(pref) { it.toMutableMap().apply { applyReminderOverride(calendarId, override) } }
}

private fun parseRecentTimeZones(stored: String?): List<String> =
    stored?.split(',').orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && runCatching { ZoneId.of(it) }.isSuccess }
        .distinct()
        .take(SettingsPrefs.MAX_RECENT_TIME_ZONES)

private fun parseFormFields(stored: String?): Set<EventFormField> = when (stored) {
    null -> SettingsPrefs.DEFAULT_FORM_FIELDS
    else -> stored.split(',')
        .mapNotNull { name -> EventFormField.entries.firstOrNull { it.name == name.trim() } }
        .toSet()
}

/** Parse a plain comma-joined view order, completed to every implemented view. */
private fun parseViewOrder(stored: String?): List<CalendarView> =
    completeViewOrder(
        stored?.split(',').orEmpty()
            .mapNotNull { name -> CalendarView.entries.firstOrNull { it.name == name.trim() } },
    )

/** Parse the quick-switch config; "!"-prefixed names are disabled. */
private fun parseQuickSwitch(stored: String?): QuickSwitchConfig {
    if (stored == null) return QuickSwitchConfig.Default
    val parsed = stored.split(',').mapNotNull { raw ->
        val token = raw.trim()
        val disabled = token.startsWith("!")
        val name = if (disabled) token.drop(1) else token
        CalendarView.entries.firstOrNull { it.name == name }?.let { it to disabled }
    }
    val order = completeViewOrder(parsed.map { it.first })
    // Only views explicitly stored disabled are excluded; anything appended
    // (a view added in a later release) defaults into the cycle.
    val disabled = parsed.filter { it.second }.map { it.first }.toSet()
    return QuickSwitchConfig(order, order.filterNot { it in disabled }.toSet())
}

private fun serializeQuickSwitch(config: QuickSwitchConfig): String =
    completeViewOrder(config.order).joinToString(",") { view ->
        if (view in config.enabled) view.name else "!${view.name}"
    }

/** Keep stored order; insert the new multi-day view after Week on upgrades. */
private fun completeViewOrder(seen: List<CalendarView>): List<CalendarView> {
    val ordered = seen.distinct().toMutableList()
    IMPLEMENTED_VIEWS.forEach { view ->
        if (view !in ordered) {
            val index = if (view == CalendarView.MultiDay && CalendarView.Week in ordered) {
                ordered.indexOf(CalendarView.Week) + 1
            } else {
                ordered.size
            }
            ordered.add(index, view)
        }
    }
    return ordered
}

/** Snapshot of the special-dates mirror's last outcome (see [SettingsPrefs.specialDatesStatus]). */
data class SpecialDatesStatus(
    /** Epoch millis of the last sync, or 0 if it has never run. */
    val lastRun: Long,
    /** Non-null when the mirror is paused and why; null when healthy. */
    val stalled: SpecialDatesStalledReason?,
)

/** Why the special-dates mirror is paused. */
enum class SpecialDatesStalledReason {
    /** READ_CONTACTS was revoked in system settings after the feature was enabled. */
    PermissionRevoked,
}

/** Snapshot of the automatic backup's last outcome (see [SettingsPrefs.autoBackupStatus]). */
data class BackupStatus(
    /** Epoch millis of the last run, or 0 if it has never run. */
    val lastRun: Long,
    val lastSuccess: Boolean,
    val consecutiveFailures: Int,
)

/**
 * The lead times to prefill on a new event: the matching per-calendar override
 * if [calendarId] has one for this event kind, otherwise the global default for
 * that kind. All-day events consult [allDayOverrides] / [allDayGlobal]; timed
 * events consult [timedOverrides] / [timedGlobal]. The empty list = no reminder.
 * Pure so it can be unit-tested.
 */
fun resolveDefaultReminder(
    timedGlobal: List<Int>,
    allDayGlobal: List<Int>,
    timedOverrides: Map<Long, List<Int>>,
    allDayOverrides: Map<Long, List<Int>>,
    calendarId: Long?,
    isAllDay: Boolean,
): List<Int> {
    val overrides = if (isAllDay) allDayOverrides else timedOverrides
    val global = if (isAllDay) allDayGlobal else timedGlobal
    return if (calendarId != null && overrides.containsKey(calendarId)) {
        overrides.getValue(calendarId)
    } else {
        global
    }
}

/**
 * The length a new timed event on [calendarId] opens with: that calendar's
 * override if it has one, otherwise the [global] default. Pure so it can be
 * unit-tested.
 */
fun resolveDefaultEventDuration(
    global: Int,
    overrides: Map<Long, Int>,
    calendarId: Long?,
): Int = calendarId?.let { overrides[it] } ?: global

/** Sentinel stored for [WeekStartPref.Auto]; days store their [DayOfWeek.name]. */
private const val WEEK_START_AUTO = "AUTO"

private fun WeekStartPref.storageValue(): String = when (this) {
    WeekStartPref.Auto -> WEEK_START_AUTO
    is WeekStartPref.Day -> day.name
}

/**
 * Parse the stored week-start value. "AUTO"/null/garbage → [WeekStartPref.Auto];
 * a [DayOfWeek] name → [WeekStartPref.Day]. The legacy "MONDAY"/"SUNDAY" enum
 * values migrate transparently, since both are valid day names.
 */
private fun parseWeekStart(stored: String?): WeekStartPref = when (stored) {
    null, WEEK_START_AUTO -> WeekStartPref.Auto
    else -> DayOfWeek.entries.firstOrNull { it.name == stored }
        ?.let { WeekStartPref.Day(it) } ?: WeekStartPref.Auto
}

private const val NONE = "none"
private const val ENTRY_SEP = ";"
private const val KEY_VALUE_SEP = "="
private const val LIST_SEP = ","

/**
 * The per-calendar override map codec, in Calendula's stored dialect
 * (`id=minutes` entries joined by `;`, minutes comma-joined, `none` for an
 * explicit no-reminder). The model and codec live in floret-kit; the dialect
 * (fixed at release) stays here.
 */
private val reminderOverrideCodec =
    ReminderOverrideCodec(entrySep = ENTRY_SEP, keyValueSep = KEY_VALUE_SEP, listSep = LIST_SEP, noneToken = NONE)

/**
 * Parse a stored reminder value into lead times. `null`/empty/"none" → empty
 * list; a comma-joined list → its minutes; a legacy single value ("30") → a
 * one-element list. Non-numeric parts are dropped defensively.
 */
private fun String?.toReminderList(): List<Int> = when {
    this == null || isEmpty() || this == NONE -> emptyList()
    else -> split(LIST_SEP).mapNotNull { it.trim().toIntOrNull() }.normalizeReminders()
}

/** Serialise lead times for storage: "none" when empty, else comma-joined. */
private fun List<Int>.toStoredReminders(): String =
    if (isEmpty()) NONE else normalizeReminders().joinToString(LIST_SEP) { it.toString() }

/**
 * Parse the per-calendar duration map (`id=minutes` entries joined by `;`).
 * Malformed entries and out-of-range lengths are dropped, so a garbled value
 * degrades to "inherits the global default" instead of throwing.
 */
private fun parseDurationOverrides(stored: String?): Map<Long, Int> =
    stored?.split(ENTRY_SEP).orEmpty().mapNotNull { entry ->
        val parts = entry.split(KEY_VALUE_SEP)
        if (parts.size != 2) return@mapNotNull null
        val calendarId = parts[0].trim().toLongOrNull() ?: return@mapNotNull null
        val minutes = parts[1].trim().toIntOrNull()
            ?.takeIf { it in SettingsPrefs.MIN_EVENT_DURATION..SettingsPrefs.MAX_EVENT_DURATION }
            ?: return@mapNotNull null
        calendarId to minutes
    }.toMap()

private fun Map<Long, Int>.toStoredDurations(): String =
    entries.sortedBy { it.key }.joinToString(ENTRY_SEP) { (id, minutes) ->
        "$id$KEY_VALUE_SEP$minutes"
    }

