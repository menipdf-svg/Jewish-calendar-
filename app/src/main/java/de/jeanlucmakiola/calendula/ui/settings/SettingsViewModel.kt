package de.jeanlucmakiola.calendula.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.appname.LauncherName
import de.jeanlucmakiola.calendula.data.appname.LauncherNameManager
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesCalendarSpec
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesScheduler
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesSyncEngine
import de.jeanlucmakiola.calendula.data.contacts.resolveTitleTemplate
import de.jeanlucmakiola.calendula.data.fonts.CustomFontStore
import de.jeanlucmakiola.floret.android.stateWhileSubscribed
import de.jeanlucmakiola.floret.di.IoDispatcher
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.reminderOverrideFor
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.SettingsKeys
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.specialDatesCalendars
import de.jeanlucmakiola.calendula.data.prefs.specialDatesStatus
import de.jeanlucmakiola.calendula.data.prefs.specialDatesTitleTemplates
import de.jeanlucmakiola.calendula.data.prefs.specialDatesTypes
import de.jeanlucmakiola.calendula.data.prefs.timelineHours
import de.jeanlucmakiola.floret.prefs.get
import de.jeanlucmakiola.calendula.data.prefs.SpecialDatesStalledReason
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.reminders.ReminderScanner
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.TimelineHours
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.theme.AppFontSettings
import de.jeanlucmakiola.calendula.ui.theme.FONT_CUSTOM_TOKEN
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
    repository: CalendarRepository,
    private val specialDatesEngine: SpecialDatesSyncEngine,
    specialDatesSpec: SpecialDatesCalendarSpec,
    private val launcherNameManager: LauncherNameManager,
    private val reminderScanner: ReminderScanner,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Writable calendars that are switched on — the only ones that take a
     * per-calendar reminder override, since a switched-off one plans no
     * reminders at all (#75).
     */
    private val writableCalendars: Flow<List<CalendarSource>> = repository.calendars()
        .map { calendars -> calendars.filter { it.canModifyContents && it.isVisibleInSystem } }
        .catch { emit(emptyList()) }

    val state: StateFlow<SettingsUiState> =
        combine(prefs.data, writableCalendars) { stored, calendars -> stored.toUiState(calendars) }
            .stateWhileSubscribed(
                scope = viewModelScope,
                initialValue = SettingsUiState(dynamicColorAvailable = dynamicColorAvailable),
            )

    private fun Preferences.toUiState(calendars: List<CalendarSource>) = SettingsUiState(
        themeMode = this[SettingsKeys.themeMode],
        dynamicColor = this[SettingsKeys.dynamicColor] && dynamicColorAvailable,
        dynamicColorAvailable = dynamicColorAvailable,
        softenColors = this[SettingsKeys.softenCalendarColors],
        weekStart = this[SettingsKeys.weekStart],
        timeFormat = this[SettingsKeys.timeFormat],
        showHourGrid = this[SettingsKeys.showHourGrid],
        multiDayCount = this[SettingsKeys.multiDayCount],
        pastEventDisplay = this[SettingsKeys.pastEventDisplay],
        dimCompletedEvents = this[SettingsKeys.dimCompletedEvents],
        showWeekNumbers = this[SettingsKeys.showWeekNumbers],
        monthShowStartTimes = this[SettingsKeys.monthShowStartTimes],
        todayButtonInToolbar = this[SettingsKeys.todayButtonInToolbar],
        dragToReschedule = this[SettingsKeys.dragToReschedule],
        tapOpensEditor = this[SettingsKeys.tapOpensEditor],
        agendaScreenRange = this[SettingsKeys.agendaScreenRange],
        agendaShowToday = this[SettingsKeys.agendaShowToday],
        agendaShowRangeBar = this[SettingsKeys.agendaShowRangeBar],
        defaultView = this[SettingsKeys.defaultView],
        quickSwitchConfig = this[SettingsKeys.quickSwitchConfig],
        monthViewStyle = this[SettingsKeys.monthViewStyle],
        timelineScale = this[SettingsKeys.timelineScale],
        timelineHours = timelineHours,
        drawerViewOrder = this[SettingsKeys.drawerViewOrder],
        defaultFormFields = this[SettingsKeys.defaultFormFields],
        autofocusEventTitle = this[SettingsKeys.autofocusEventTitle],
        defaultEventDurationMinutes = this[SettingsKeys.defaultEventDurationMinutes],
        perCalendarEventDuration = this[SettingsKeys.perCalendarEventDuration],
        remindersEnabled = this[SettingsKeys.remindersEnabled],
        defaultReminderMinutes = this[SettingsKeys.defaultReminderMinutes],
        defaultAllDayReminderMinutes = this[SettingsKeys.defaultAllDayReminderMinutes],
        allDayReminderTimeMinutes = this[SettingsKeys.allDayReminderTimeMinutes],
        snoozeMinutes = this[SettingsKeys.snoozeMinutes],
        perCalendarReminderOverride = this[SettingsKeys.perCalendarReminderOverride],
        perCalendarAllDayReminderOverride = this[SettingsKeys.perCalendarAllDayReminderOverride],
        writableCalendars = calendars,
        allowColorOnUnsupportedCalendars = this[SettingsKeys.allowColorOnUnsupportedCalendars],
        managedCalendarIds = specialDatesCalendars.values.toSet(),
    )

    /**
     * The app-wide custom-font choice (issue #19). Its own flow — both this
     * screen and [MainActivity] (which builds the typography) read it
     * independently of the main settings state.
     */
    val fontState: StateFlow<AppFontSettings> = prefs.data.map {
        AppFontSettings(
            brand = it[SettingsKeys.brandFont],
            plain = it[SettingsKeys.plainFont],
            brandStamp = it[SettingsKeys.brandFontStamp],
            plainStamp = it[SettingsKeys.plainFontStamp],
        )
    }.stateWhileSubscribed(
        scope = viewModelScope,
        initialValue = AppFontSettings(),
    )

    /**
     * The launcher-label choice (#44), backed by the manifest aliases'
     * component-enabled state rather than a preference. Seeded with the manifest
     * default and corrected off-thread — `getComponentEnabledSetting` is a
     * binder round-trip and this ViewModel is built on the main thread.
     */
    private val _launcherName = MutableStateFlow(LauncherName.CALENDULA)
    val launcherName: StateFlow<LauncherName> = _launcherName.asStateFlow()

    init {
        viewModelScope.launch {
            _launcherName.value = withContext(io) { launcherNameManager.current() }
        }
    }

    // Emitted when a picked font file couldn't be read as a font; the screen
    // surfaces it and the previous selection stays put.
    private val _fontImportFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fontImportFailed: SharedFlow<Unit> = _fontImportFailed

    /** Contact special-dates sub-page (issue #15). */
    val specialDatesState: StateFlow<SpecialDatesUiState> = prefs.data.map { stored ->
        val templates = stored.specialDatesTitleTemplates
        val allDayOverrides = stored[SettingsKeys.perCalendarAllDayReminderOverride]
        val status = stored.specialDatesStatus
        SpecialDatesUiState(
            enabled = stored[SettingsKeys.specialDatesEnabled],
            types = stored.specialDatesTypes,
            // A blank stored template resolves to the localized default so the
            // editor always shows something sensible.
            titleTemplates = SpecialDateType.entries.associateWith { type ->
                specialDatesSpec.resolveTitleTemplate(type, templates)
            },
            // An override is always seeded on creation; present-empty = None.
            reminderChoices = stored.specialDatesCalendars.mapValues { (_, calendarId) ->
                allDayOverrides.reminderOverrideFor(calendarId)
            },
            showYear = stored[SettingsKeys.specialDatesShowYear],
            stalledPermission = status.stalled == SpecialDatesStalledReason.PermissionRevoked,
            lastRun = status.lastRun,
        )
    }.stateWhileSubscribed(
        scope = viewModelScope,
        initialValue = SpecialDatesUiState(),
    )

    /**
     * Turn the mirror on or off. Enabling schedules the daily job and runs an
     * immediate sync (which creates the calendars); disabling cancels the job and
     * tears the managed calendars down. The caller must have READ_CONTACTS granted
     * before enabling.
     */
    fun setSpecialDatesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDatesEnabled(enabled)
            if (enabled) {
                SpecialDatesScheduler.apply(appContext, enabled = true)
                SpecialDatesScheduler.runNow(appContext)
            } else {
                SpecialDatesScheduler.apply(appContext, enabled = false)
                withContext(io) { specialDatesEngine.teardown() }
            }
        }
    }

    fun setSpecialDateTypeEnabled(type: SpecialDateType, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDateTypeEnabled(type, enabled)
            // Reconcile now: create the new type's calendar, or delete a disabled one.
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    fun setSpecialDatesTitleTemplate(type: SpecialDateType, template: String) {
        viewModelScope.launch {
            prefs.setSpecialDatesTitleTemplate(type, template.trim())
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    fun setSpecialDatesShowYear(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDatesShowYear(enabled)
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    /**
     * Set a type's managed-calendar reminder default and apply it to all its
     * existing events (managed calendars own their reminders calendar-wide).
     */
    fun setSpecialDatesReminders(type: SpecialDateType, override: ReminderOverride) {
        viewModelScope.launch { withContext(io) { specialDatesEngine.applyReminders(type, override) } }
    }

    /** Kick off an immediate reconcile (the "Sync now" action). */
    fun syncSpecialDatesNow() = SpecialDatesScheduler.runNow(appContext)

    fun setThemeMode(mode: ThemeMode) = save { setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) = save { setDynamicColor(enabled) }

    fun setSoftenColors(enabled: Boolean) = save { setSoftenCalendarColors(enabled) }

    fun setFont(role: FontRole, token: String) = save {
        when (role) {
            FontRole.BRAND -> setBrandFont(token)
            FontRole.PLAIN -> setPlainFont(token)
        }
    }

    /**
     * Import a user-picked font file for [role]. Copies and validates it off the
     * main thread; on success the role switches to the custom font, otherwise the
     * previous choice is kept and [fontImportFailed] fires.
     */
    fun importCustomFont(role: FontRole, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(io) { CustomFontStore.import(appContext, role, uri) }
            if (ok) {
                // Bump the stamp so replacing an already-active custom font
                // still breaks equality and refreshes the resolved typeface.
                prefs.bumpCustomFontStamp(role)
                setFont(role, FONT_CUSTOM_TOKEN)
            } else {
                _fontImportFailed.emit(Unit)
            }
        }
    }

    fun setWeekStart(pref: WeekStartPref) = save { setWeekStart(pref) }

    fun setAgendaScreenRange(range: AgendaRange) = save { setAgendaScreenRange(range) }

    fun setAgendaShowRangeBar(enabled: Boolean) = save { setAgendaShowRangeBar(enabled) }

    fun setAgendaShowToday(enabled: Boolean) = save { setAgendaShowToday(enabled) }

    fun setTimeFormat(pref: TimeFormatPref) = save { setTimeFormat(pref) }

    fun setMultiDayCount(count: Int) = save { setMultiDayCount(count) }

    fun setShowHourGrid(enabled: Boolean) = save { setShowHourGrid(enabled) }

    fun setShowWeekNumbers(enabled: Boolean) = save { setShowWeekNumbers(enabled) }

    fun setMonthShowStartTimes(enabled: Boolean) = save { setMonthShowStartTimes(enabled) }

    fun setTodayButtonInToolbar(enabled: Boolean) = save { setTodayButtonInToolbar(enabled) }

    fun setDragToReschedule(enabled: Boolean) = save { setDragToReschedule(enabled) }

    fun setTapOpensEditor(enabled: Boolean) = save { setTapOpensEditor(enabled) }

    /**
     * Switch the launcher label (#44). The card highlights at once, then settles
     * on what the component state reports; the writes are binder round-trips and
     * run off the main thread.
     */
    fun setLauncherName(name: LauncherName) {
        _launcherName.value = name
        viewModelScope.launch {
            _launcherName.value = withContext(io) {
                launcherNameManager.set(name)
                launcherNameManager.current()
            }
        }
    }

    fun setPastEventDisplay(mode: PastEventDisplay) = save { setPastEventDisplay(mode) }

    fun setDimCompletedEvents(enabled: Boolean) = save { setDimCompletedEvents(enabled) }

    fun setDefaultView(view: CalendarView) = save { setDefaultView(view) }

    fun setFormFieldDefault(field: EventFormField, enabled: Boolean) = save { setFormFieldDefault(field, enabled) }

    /**
     * Enable or disable [view] in the quick-switch cycle, via an atomic
     * read-modify-write so a concurrent reorder can't clobber it. Any number of
     * views may be turned off; below two the pill hides itself (#150).
     */
    fun setQuickSwitchViewEnabled(view: CalendarView, enabled: Boolean) = save {
        updateQuickSwitch { config ->
            config.copy(enabled = if (enabled) config.enabled + view else config.enabled - view)
        }
    }

    /** Reorder the quick-switch views (atomic read-modify-write; keeps the enabled set). */
    fun setQuickSwitchOrder(order: List<CalendarView>) = save { updateQuickSwitch { it.copy(order = order) } }

    fun setMonthViewStyle(style: MonthViewStyle) = save { setMonthViewStyle(style) }

    fun setTimelineScale(scale: TimelineScale) = save { setTimelineScale(scale) }

    fun setTimelineHours(hours: TimelineHours) = save { setTimelineHours(hours) }

    fun setDrawerViewOrder(order: List<CalendarView>) = save { setDrawerViewOrder(order) }

    /**
     * A scan follows the write: switching reminders off cancels the scan alarm,
     * so switching them back on has to arm a new one (#75).
     */
    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setRemindersEnabled(enabled)
            reminderScanner.scan()
        }
    }

    fun setAutofocusEventTitle(enabled: Boolean) = save { setAutofocusEventTitle(enabled) }

    fun setDefaultEventDuration(minutes: Int) = save { setDefaultEventDurationMinutes(minutes) }

    /** [minutes] null clears the override, so the calendar inherits the default. */
    fun setCalendarEventDuration(calendarId: Long, minutes: Int?) = save { setCalendarEventDuration(calendarId, minutes) }

    fun setDefaultReminderMinutes(minutes: List<Int>) = save { setDefaultReminderMinutes(minutes) }

    fun setDefaultAllDayReminderMinutes(minutes: List<Int>) = save { setDefaultAllDayReminderMinutes(minutes) }

    /** The armed alarm was planned for the old hour, so re-plan against the new one. */
    fun setAllDayReminderTimeMinutes(minutesOfDay: Int) {
        viewModelScope.launch {
            prefs.setAllDayReminderTimeMinutes(minutesOfDay)
            reminderScanner.scan()
        }
    }

    fun setSnoozeMinutes(minutes: Int) = save { setSnoozeMinutes(minutes) }

    fun setCalendarReminderOverride(calendarId: Long, override: ReminderOverride) = save { setCalendarReminderOverride(calendarId, override) }

    fun setCalendarAllDayReminderOverride(calendarId: Long, override: ReminderOverride) = save { setCalendarAllDayReminderOverride(calendarId, override) }

    fun setAllowColorOnUnsupportedCalendars(enabled: Boolean) = save { setAllowColorOnUnsupportedCalendars(enabled) }

    private fun save(write: suspend SettingsPrefs.() -> Unit) {
        viewModelScope.launch { prefs.write() }
    }
}
