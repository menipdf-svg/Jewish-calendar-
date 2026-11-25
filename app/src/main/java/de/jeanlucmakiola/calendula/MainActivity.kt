package de.jeanlucmakiola.calendula

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.OpenableColumns
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.floret.locale.AppLanguage
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.floret.prefs.isDark
import de.jeanlucmakiola.floret.prefs.isPureBlack
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.buildInsertEventForm
import de.jeanlucmakiola.calendula.domain.sharedTextOrNull
import de.jeanlucmakiola.calendula.ui.ExternalRequests
import de.jeanlucmakiola.calendula.ui.InsertRequest
import de.jeanlucmakiola.calendula.ui.RootScreen
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourGrid
import de.jeanlucmakiola.calendula.ui.common.LocalMultiDayCount
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.WidgetNavRequest
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.detail.EventDetailViewModel.Companion.NO_OCCURRENCE_TIME
import de.jeanlucmakiola.calendula.ui.edit.ImportSource
import de.jeanlucmakiola.floret.components.DebugRibbon
import de.jeanlucmakiola.calendula.ui.crash.CrashReportActivity
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.ui.settings.SettingsViewModel
import de.jeanlucmakiola.floret.crash.CrashReportDialog
import de.jeanlucmakiola.floret.crash.CrashReporter
import de.jeanlucmakiola.floret.crash.submitCrashReport
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme
import de.jeanlucmakiola.calendula.ui.theme.WindowThemeCache
import de.jeanlucmakiola.calendula.ui.theme.windowBackgroundRes
import de.jeanlucmakiola.calendula.ui.theme.calendulaTypography
import de.jeanlucmakiola.calendula.ui.theme.resolveFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var calendarRepository: CalendarRepository
    @Inject lateinit var settingsPrefs: SettingsPrefs

    // Which of light/dark the system bars are drawn for. The styles installed
    // in onCreate read this field live, so androidx's config-change replay
    // picks up the in-app override instead of the night resource qualifier.
    private var systemBarsDark = false

    private val requests = ExternalRequests(
        onExternalFormClosed = {
            insertFormOpen = false
            if (launchedForInsert) finish()
        },
    )

    // Whether such a form is still open. Only then does a recreation re-read the
    // intent to bring it back; a saved or dismissed one stays closed.
    private var insertFormOpen = false

    // Started just for an external create, so closing its form returns to the
    // app that asked instead of leaving the calendar on top of it.
    private var launchedForInsert = false

    // A captured crash report awaiting the user's decision, surfaced as a dialog
    // over the calendar on the next launch (the single-crash path). A startup
    // crash-loop is handled out of band, before setContent — see below.
    private var pendingCrashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the app keeps crashing as it starts, the main UI can't be trusted
        // to come up — route to the standalone report screen instead of
        // re-entering the crashing graph.
        if (CrashReporter.isCrashLoop(this)) {
            startActivity(
                Intent(this, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            return
        }

        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        // The stored theme only arrives after the first frame, so a Light, Dark
        // or Black override starts from its last mirrored copy: the matching
        // backdrop and bar icons instead of the night qualifier's (#181).
        val cachedTheme = WindowThemeCache.read(this)
        cachedTheme?.windowBackgroundRes()?.let { window.setBackgroundDrawableResource(it) }
        systemBarsDark = (cachedTheme ?: ThemeMode.SYSTEM).isDark(systemDark)
        applyEdgeToEdge()
        val appearance = MutableStateFlow(
            Appearance(
                themeMode = cachedTheme ?: ThemeMode.SYSTEM,
                dynamicColor = WindowThemeCache.readDynamicColor(this),
            ),
        )
        lifecycleScope.launch {
            combine(settingsPrefs.themeMode, settingsPrefs.dynamicColor, ::Appearance).collect { stored ->
                WindowThemeCache.write(this@MainActivity, stored.themeMode, stored.dynamicColor ?: true)
                window.setBackgroundDrawableResource(stored.themeMode.windowBackgroundRes() ?: R.color.window_background)
                appearance.value = stored
            }
        }
        // Android hands the intent that started the task back to onCreate every
        // time the activity is recreated — on a rotation, or on a restart after
        // the system reclaimed the process. Re-running a launch that opens one
        // specific event is what made a single reminder or widget tap re-open
        // that event on every later launch, an error screen once the event had
        // been deleted (#309); the user left it long ago, and the activity
        // restores where they actually were along with its saved state.
        //
        // Only those channels are held back. An .ics import or a prefilled
        // create form keeps no saved state of its own, so re-reading the intent
        // is what carries one through a recreation — the create form only while
        // it's still open.
        val replaying = savedInstanceState != null || intent.isRelaunch()
        requests.replayed = savedInstanceState != null
        if (!replaying) {
            requests.detailKey = intent.detailKeyOrNull() ?: intent.viewEventKeyOrNull()
            requests.editKey = intent.editEventKeyOrNull()
        }
        requests.nav = intent.navRequestOrNull()
            ?.takeUnless { replaying && it is WidgetNavRequest.OpenEvent }
        requests.importUri = intent.importUriOrNull()
        val insertOpenBefore = savedInstanceState?.getBoolean(KEY_INSERT_FORM_OPEN) == true
        requests.insert = intent.createRequestOrNull()?.takeIf { !replaying || insertOpenBefore }
        insertFormOpen = requests.insert != null
        launchedForInsert = savedInstanceState?.getBoolean(KEY_LAUNCHED_FOR_INSERT)
            ?: (requests.insert != null)
        if (CrashReporter.shouldPrompt(this)) pendingCrashReport = CrashReporter.pendingReport(this)
        setContent {
            // One activity-scoped SettingsViewModel drives both the theme here
            // and the Settings screen, so a theme change applies app-wide at once.
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()
            // Seeded from the cache, so the first frame doesn't draw the default
            // theme and palette before the stored choices arrive.
            val shownAppearance by appearance.collectAsStateWithLifecycle()
            val themeMode = shownAppearance.themeMode
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            // onCreate can only see the night resource qualifier, not the in-app
            // override — re-apply from the resolved theme so the bar icons follow
            // the app's light/dark choice.
            DisposableEffect(darkTheme) {
                systemBarsDark = darkTheme
                applyEdgeToEdge()
                onDispose {}
            }
            // The app-wide clock convention: the time-format preference resolved
            // against the device's 24-hour system setting, provided once here so
            // every time label reads it via LocalUse24HourFormat.
            val context = LocalContext.current
            val use24Hour = remember(settings.timeFormat, context) {
                settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(context))
            }
            // The user's custom-font choice, resolved to a Material typography
            // (issue #19). Recomputed only when a token — or the custom-font
            // re-import stamp AppFontSettings carries, so replacing the file
            // behind an active "custom" token still refreshes — changes;
            // "system for both" returns the default scale untouched.
            // The timeline scale plus the pinch in flight over it (#56). Held
            // here, above the calendar views, so a zoom survives paging between
            // weeks and switching between the week and day view.
            val timelineZoom = rememberTimelineZoom(
                stored = settings.timelineScale,
                hours = settings.timelineHours,
                onPersist = settingsViewModel::setTimelineScale,
            )
            val fonts by settingsViewModel.fontState.collectAsStateWithLifecycle()
            val typography = remember(fonts, context) {
                calendulaTypography(
                    brand = resolveFontFamily(fonts.brand, FontRole.BRAND, context),
                    plain = resolveFontFamily(fonts.plain, FontRole.PLAIN, context),
                )
            }
            CalendulaTheme(
                darkTheme = darkTheme,
                dynamicColor = shownAppearance.dynamicColor ?: settings.dynamicColor,
                pureBlack = themeMode.isPureBlack,
                typography = typography,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalUse24HourFormat provides use24Hour,
                        LocalShowHourGrid provides settings.showHourGrid,
                        LocalTimelineZoom provides timelineZoom,
                        LocalMultiDayCount provides settings.multiDayCount,
                        LocalSoftenColors provides settings.softenColors,
                    ) {
                        RootScreen(
                            modifier = Modifier.fillMaxSize(),
                            requests = requests,
                        )
                    }
                    // A persistent corner marker so a debug build is never
                    // mistaken for the production app; compiled out of release.
                    if (BuildConfig.DEBUG) DebugRibbon()
                }
                pendingCrashReport?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onSend = {
                            submitCrashReport(this@MainActivity, report)
                            CrashReporter.clearReport(this@MainActivity)
                            pendingCrashReport = null
                        },
                        onDismiss = {
                            // Keep the report (Settings can still reach it); just
                            // stop it popping on every launch.
                            CrashReporter.dismissPrompt(this@MainActivity)
                            pendingCrashReport = null
                        },
                    )
                }
            }
        }
    }

    /**
     * Applies the transparent edge-to-edge bars for the current [systemBarsDark].
     * Both scrims are transparent because API 29+ enforces its own contrast and
     * ignores them anyway.
     */
    private fun applyEdgeToEdge() {
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparent, transparent) { systemBarsDark },
            navigationBarStyle = SystemBarStyle.auto(transparent, transparent) { systemBarsDark },
        )
    }

    override fun onStart() {
        super.onStart()
        // Changes made while we were away may never have notified us (#364).
        calendarRepository.refresh()
    }

    override fun onResume() {
        super.onResume()
        // Reaching a running UI means startup succeeded; reset the loop trail.
        CrashReporter.markHealthy(this)
        AppLanguage.syncMirror(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_INSERT_FORM_OPEN, insertFormOpen)
        outState.putBoolean(KEY_LAUNCHED_FOR_INSERT, launchedForInsert)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requests.replayed = false
        (intent.detailKeyOrNull() ?: intent.viewEventKeyOrNull())?.let { requests.detailKey = it }
        intent.navRequestOrNull()?.let { requests.nav = it }
        intent.importUriOrNull()?.let { requests.importUri = it }
        intent.createRequestOrNull()?.let {
            requests.insert = it
            insertFormOpen = true
        }
        intent.editEventKeyOrNull()?.let { requests.editKey = it }
        // The user moved on into the app, so closing the create form no longer
        // hands control back to the app that started it.
        if (requests.detailKey != null || requests.nav != null || requests.importUri != null ||
            requests.editKey != null
        ) {
            launchedForInsert = false
        }
    }

    /**
     * Whether this launch is the task being resumed rather than a fresh
     * delivery — Android sets the flag when an activity is started from the
     * recents list, where the intent it carries is the one the task was started
     * with however long ago. `onNewIntent` is the channel a real new request
     * arrives on while the task lives, so nothing is lost by ignoring these.
     */
    private fun Intent.isRelaunch(): Boolean =
        flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

    /**
     * The `.ics` Uri an external app asked us to open (file manager `ACTION_VIEW`)
     * or share into us (`ACTION_SEND`). Restricted to content/file schemes so the
     * app's own `calendula://` deep-links never match.
     */
    private fun Intent.importUriOrNull(): Uri? {
        val uri = when (action) {
            Intent.ACTION_VIEW -> data
            // Shared text is a create (#306); a text/plain file is imported only
            // when it is calendar data, e.g. an .ics attachment labelled text/plain.
            Intent.ACTION_SEND -> sharedStreamOrNull()
                ?.takeUnless { type == SHARED_TEXT_TYPE && !it.isCalendarFile() }
            else -> null
        } ?: return null
        // The calendar "view time" Uri (a date tap) is also ACTION_VIEW/content;
        // it's a navigation, not a file to import, so [navRequestOrNull] owns it.
        if (uri.host == CALENDAR_PROVIDER_HOST) return null
        return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
    }

    /**
     * A prefilled new-event form from an external launch asking us to create an
     * event — another app or widget (e.g. Todo Agenda) firing `ACTION_INSERT`
     * (issue #30), or `ACTION_EDIT` with no concrete event id (AOSP's "edit a new
     * event", i.e. create). The new event's fields ride as CalendarContract
     * extras; anything omitted falls back to the in-app "new event" defaults in
     * [buildInsertEventForm]. An intent that names no end time leaves the length
     * to the default-duration setting ([ImportSource.InsertOpenEnded], #54).
     */
    private fun Intent.insertRequestOrNull(): InsertRequest? {
        // ACTION_EDIT / ACTION_INSERT_OR_EDIT on an existing event route to the
        // edit form instead ([editEventKeyOrNull]); an id-less one is a create,
        // as is any plain ACTION_INSERT.
        val isCreate = action == Intent.ACTION_INSERT ||
            ((action == Intent.ACTION_EDIT || action == Intent.ACTION_INSERT_OR_EDIT) &&
                editEventKeyOrNull() == null)
        if (!isCreate) return null
        val beginMillis = longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME)
        val endMillis = longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME)
        val form = buildInsertEventForm(
            beginMillis = beginMillis,
            endMillis = endMillis,
            isAllDay = getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
            title = getStringExtra(CalendarContract.Events.TITLE),
            description = getStringExtra(CalendarContract.Events.DESCRIPTION),
            location = getStringExtra(CalendarContract.Events.EVENT_LOCATION),
            rrule = getStringExtra(CalendarContract.Events.RRULE),
            zone = TimeZone.currentSystemDefault(),
            now = Clock.System.now(),
        )
        // An end the intent didn't name — or one [buildInsertEventForm] drops for
        // landing before the start — leaves the length to the setting.
        val namesEnd = beginMillis != null && endMillis != null && endMillis >= beginMillis
        return InsertRequest(form, if (namesEnd) ImportSource.Insert else ImportSource.InsertOpenEnded)
    }

    /** Any external request to create an event: shared text or an insert intent. */
    private fun Intent.createRequestOrNull(): InsertRequest? = sharedTextRequestOrNull() ?: insertRequestOrNull()

    /**
     * Plain text shared into us (`ACTION_SEND`) or picked from a text-selection
     * menu (`ACTION_PROCESS_TEXT`), as a new event at the default time (#306).
     */
    private fun Intent.sharedTextRequestOrNull(): InsertRequest? {
        val shared = when (action) {
            Intent.ACTION_SEND -> if (type == SHARED_TEXT_TYPE && sharedStreamOrNull()?.isCalendarFile() != true) {
                sharedTextOrNull(
                    subject = getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                    text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                )
            } else {
                null
            }
            Intent.ACTION_PROCESS_TEXT ->
                sharedTextOrNull(subject = null, text = getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            else -> null
        } ?: return null
        val form = buildInsertEventForm(
            title = shared.title,
            description = shared.description,
            zone = TimeZone.currentSystemDefault(),
            now = Clock.System.now(),
        )
        return InsertRequest(form, ImportSource.InsertOpenEnded)
    }

    private fun Intent.sharedStreamOrNull(): Uri? =
        IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)

    /** Whether a shared file is iCalendar/vCalendar data, by its resolved type or its name. */
    private fun Uri.isCalendarFile(): Boolean {
        val resolvedType = runCatching { contentResolver.getType(this) }.getOrNull()
        if (resolvedType in CALENDAR_TYPES) return true
        val name = when (scheme) {
            "content" -> runCatching {
                contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            else -> lastPathSegment
        } ?: return false
        return CALENDAR_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
    }

    /** A Long extra's value, or null when the extra is absent. */
    private fun Intent.longExtraOrNull(key: String): Long? =
        if (hasExtra(key)) getLongExtra(key, 0L) else null

    /**
     * The date a launcher/clock date tap points at, parsed from the AOSP calendar
     * "view time" intent: ACTION_VIEW on `content://com.android.calendar/time/
     * <epochMillis>`. Null for any other intent. The matching manifest filter is
     * what lets users pick Calendula from the system calendar chooser (issue #9).
     */
    private fun Intent.calendarTimeDateOrNull(): LocalDate? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "time") return null
        val millis = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    private fun Intent.navRequestOrNull(): WidgetNavRequest? {
        // An external date tap (launcher/clock) has no widget source, so it opens
        // the day view rooted over the default home view (OpenDate source = null).
        calendarTimeDateOrNull()?.let { return WidgetNavRequest.OpenDate(it.toString(), source = null) }
        // A widget header tap: open a top-level view with no date drill-in. The
        // empty string carried by [openViewIntent] means "the default home view".
        if (hasExtra(EXTRA_OPEN_VIEW)) {
            val name = getStringExtra(EXTRA_OPEN_VIEW).orEmpty()
            return WidgetNavRequest.OpenView(CalendarView.entries.firstOrNull { it.name == name })
        }
        val source = sourceViewOrNull()
        val eventId = getLongExtra(EXTRA_EVENT_ID, -1L)
        return when {
            // Launcher long-press "New event" shortcut. Static shortcut intents
            // can't carry typed extras, so the action alone signals create-on-today.
            action == ACTION_NEW_EVENT -> WidgetNavRequest.Create(null)
            getBooleanExtra(EXTRA_CREATE, false) ->
                WidgetNavRequest.Create(getStringExtra(EXTRA_DATE_ISO))
            // A widget event tap carries both the occurrence and its source view;
            // reminders (no source) fall through to [detailKeyOrNull] instead.
            source != null && eventId != -1L -> WidgetNavRequest.OpenEvent(
                eventId = eventId,
                beginMillis = getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
                endMillis = getLongExtra(EXTRA_END_MILLIS, 0L),
                source = source,
            )
            source != null && getStringExtra(EXTRA_DATE_ISO) != null ->
                WidgetNavRequest.OpenDate(getStringExtra(EXTRA_DATE_ISO)!!, source)
            else -> null
        }
    }

    /** The view the launching widget represents, if any (see [EXTRA_SOURCE_VIEW]). */
    private fun Intent.sourceViewOrNull(): CalendarView? =
        getStringExtra(EXTRA_SOURCE_VIEW)
            ?.let { name -> CalendarView.entries.firstOrNull { it.name == name } }

    private fun Intent.detailKeyOrNull(): LongArray? {
        // A widget event tap (source present) is routed through [navRequestOrNull]
        // so it can also set the base view; only sourceless reminder taps land here.
        if (sourceViewOrNull() != null) return null
        val eventId = getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return null
        return longArrayOf(
            eventId,
            getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
            getLongExtra(EXTRA_END_MILLIS, 0L),
        )
    }

    /**
     * The detail key for an external "open this event" — ACTION_VIEW on
     * `content://com.android.calendar/events/<id>`, the way AOSP fires it (e.g.
     * tapping an existing event in the Todo Agenda widget, issue #48). Reuses the
     * same occurrence-key channel as reminder taps. The launcher passes the
     * occurrence's times as `EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME` when
     * it has them; a bare URI omits them, so we carry [NO_OCCURRENCE_TIME] and
     * [EventDetailViewModel] falls back to the event row's own DTSTART/DTEND
     * rather than rendering at the epoch.
     */
    private fun Intent.viewEventKeyOrNull(): LongArray? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "events") return null
        val eventId = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return longArrayOf(
            eventId,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ?: NO_OCCURRENCE_TIME,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME) ?: NO_OCCURRENCE_TIME,
        )
    }

    /**
     * The occurrence key for an external "edit this event" — `ACTION_EDIT` on
     * `content://com.android.calendar/events/<id>`, the way AOSP fires it (e.g.
     * an assistant, task app, or widget that wants to open the event for editing
     * rather than viewing). Opens it in the edit form. Reuses the same
     * occurrence-key channel as reminder/view taps; the caller passes the
     * occurrence's times as `EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME`
     * when it has them, otherwise we carry [NO_OCCURRENCE_TIME] and
     * [EventEditViewModel.openForEdit] falls back to the event row's own
     * DTSTART/DTEND. An id-less `ACTION_EDIT` — or `ACTION_INSERT_OR_EDIT`, which
     * some apps fire — is a create instead ([insertFormOrNull]).
     */
    private fun Intent.editEventKeyOrNull(): LongArray? {
        if (action != Intent.ACTION_EDIT && action != Intent.ACTION_INSERT_OR_EDIT) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "events") return null
        val eventId = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return longArrayOf(
            eventId,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ?: NO_OCCURRENCE_TIME,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME) ?: NO_OCCURRENCE_TIME,
        )
    }

    companion object {
        // The calendar provider's authority/host. A date tap arrives as
        // ACTION_VIEW on content://com.android.calendar/time/<epochMillis>.
        private const val CALENDAR_PROVIDER_HOST = "com.android.calendar"
        private const val SHARED_TEXT_TYPE = "text/plain"
        private val CALENDAR_TYPES = setOf("text/calendar", "text/x-vcalendar", "application/ics")
        private val CALENDAR_EXTENSIONS = listOf(".ics", ".vcs")
        private const val KEY_INSERT_FORM_OPEN = "insert_form_open"
        private const val KEY_LAUNCHED_FOR_INSERT = "launched_for_insert"

        private const val EXTRA_EVENT_ID = "de.jeanlucmakiola.calendula.extra.EVENT_ID"
        private const val EXTRA_BEGIN_MILLIS = "de.jeanlucmakiola.calendula.extra.BEGIN"
        private const val EXTRA_END_MILLIS = "de.jeanlucmakiola.calendula.extra.END"
        private const val EXTRA_DATE_ISO = "de.jeanlucmakiola.calendula.extra.DATE_ISO"
        private const val EXTRA_CREATE = "de.jeanlucmakiola.calendula.extra.CREATE"

        // A widget header tap asking to open a top-level view (no date drill-in).
        // Its value is the target [CalendarView] name, or "" for the default view.
        private const val EXTRA_OPEN_VIEW = "de.jeanlucmakiola.calendula.extra.OPEN_VIEW"

        // The [CalendarView] (by name) of the widget a launch came from. Roots the
        // in-app back stack in that view; absent for non-widget launches (reminders).
        private const val EXTRA_SOURCE_VIEW = "de.jeanlucmakiola.calendula.extra.SOURCE_VIEW"

        // Fired by the launcher long-press "New event" shortcut (res/xml/
        // shortcuts.xml hardcodes this string — keep the two in sync).
        const val ACTION_NEW_EVENT = "de.jeanlucmakiola.calendula.action.NEW_EVENT"

        /**
         * Intent opening the detail screen of one occurrence (reminder
         * notifications). The synthetic data URI keys the intent so
         * PendingIntents for different occurrences never collapse into one.
         */
        fun eventDetailIntent(
            context: Context,
            eventId: Long,
            beginMillis: Long,
            endMillis: Long,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            data = "calendula://event/$eventId/$beginMillis".toUri()
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_BEGIN_MILLIS, beginMillis)
            putExtra(EXTRA_END_MILLIS, endMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        /**
         * Open the day view anchored on [date], drilled in over the launching
         * widget's [source] view (home-screen widgets). Backing out of the day
         * returns to [source], then to the default home view.
         */
        fun openDateIntent(context: Context, date: LocalDate, source: CalendarView): Intent =
            Intent(context, MainActivity::class.java).apply {
                // The source is part of the URI so widgets of different kinds
                // tapping the same date don't share one PendingIntent.
                data = "calendula://date/$date/${source.name}".toUri()
                putExtra(EXTRA_DATE_ISO, date.toString())
                putExtra(EXTRA_SOURCE_VIEW, source.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Open one occurrence's detail from a widget event tap, rooting the back
         * stack in the widget's [source] view. Same occurrence-key shape as
         * [eventDetailIntent]; the [source] extra is what distinguishes a widget
         * tap (sets the base view) from a reminder tap (leaves it untouched).
         */
        fun openEventIntent(
            context: Context,
            eventId: Long,
            beginMillis: Long,
            endMillis: Long,
            source: CalendarView,
        ): Intent = eventDetailIntent(context, eventId, beginMillis, endMillis).apply {
            data = "calendula://event/$eventId/$beginMillis/${source.name}".toUri()
            putExtra(EXTRA_SOURCE_VIEW, source.name)
        }

        /** Open the create-event form prefilled for [date] (home-screen widgets). */
        fun openCreateIntent(context: Context, date: LocalDate): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://create/$date".toUri()
                putExtra(EXTRA_CREATE, true)
                putExtra(EXTRA_DATE_ISO, date.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Open the app on a top-level [view] with no date drill-in — a widget
         * header tap. A null [view] opens the user's default home view (the agenda
         * widget's "Upcoming" title); a concrete view roots there over the default
         * home (the month widget's month/year title → [CalendarView.Month]). The
         * per-view data URI keeps distinct headers' PendingIntents from collapsing.
         */
        fun openViewIntent(context: Context, view: CalendarView?): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://view/${view?.name ?: "default"}".toUri()
                putExtra(EXTRA_OPEN_VIEW, view?.name ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}

/** The theme choices the window needs before the settings screen's state loads. */
private data class Appearance(val themeMode: ThemeMode, val dynamicColor: Boolean?)
