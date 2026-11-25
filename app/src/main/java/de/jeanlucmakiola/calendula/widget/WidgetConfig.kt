package de.jeanlucmakiola.calendula.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.parseAgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.storageValue
import de.jeanlucmakiola.floret.glance.WidgetBackground
import de.jeanlucmakiola.floret.glance.WidgetThemeMode
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.floret.prefs.toEnum

/** A widget's own light/dark choice (#292); [FOLLOW_APP] tracks the app's theme setting. */
enum class WidgetTheme { FOLLOW_APP, LIGHT, DARK }

/** How a widget looks, set per placed widget in its config screen (#292). */
data class WidgetAppearance(
    val background: WidgetBackground = WidgetBackground.TINTED,
    val theme: WidgetTheme = WidgetTheme.FOLLOW_APP,
    /** How opaque a Solid or Tinted fill is (#489). */
    val opacity: Float = 1f,
)

/** The agenda widget's look: the 2.24 day cards, or v2.23's plain day headers and stripes (#489). */
enum class AgendaWidgetStyle { CARDS, CLASSIC }

/** What the agenda widget lists, per placed widget. */
data class AgendaWidgetOptions(
    val range: AgendaRange,
    val textSize: WidgetSize,
    val showToday: Boolean,
    val pastDisplay: PastEventDisplay,
    val style: AgendaWidgetStyle = AgendaWidgetStyle.CARDS,
    /** Classic style only: draw the text in the opposite theme's colours, for a faded fill over the wallpaper. */
    val invertText: Boolean = false,
)

internal fun WidgetTheme.resolve(app: ThemeMode): WidgetThemeMode = when (this) {
    WidgetTheme.LIGHT -> WidgetThemeMode.LIGHT
    WidgetTheme.DARK -> WidgetThemeMode.DARK
    WidgetTheme.FOLLOW_APP -> when (app) {
        ThemeMode.SYSTEM -> WidgetThemeMode.SYSTEM
        ThemeMode.LIGHT -> WidgetThemeMode.LIGHT
        ThemeMode.DARK, ThemeMode.BLACK -> WidgetThemeMode.DARK
    }
}

private val BackgroundKey = stringPreferencesKey("cfg_background")
private val ThemeKey = stringPreferencesKey("cfg_theme")
private val OpacityKey = floatPreferencesKey("cfg_opacity")
private val AgendaStyleKey = stringPreferencesKey("cfg_agenda_style")
private val InvertTextKey = booleanPreferencesKey("cfg_invert_text")
private val AgendaRangeKey = stringPreferencesKey("cfg_agenda_range")
private val TextSizeKey = stringPreferencesKey("cfg_text_size")
private val ShowTodayKey = booleanPreferencesKey("cfg_show_today")
private val PastDisplayKey = stringPreferencesKey("cfg_past_display")
private val HiddenCalendarsKey = stringSetPreferencesKey("cfg_hidden_calendars")

internal val Preferences.widgetAppearance: WidgetAppearance
    get() = WidgetAppearance(
        background = this[BackgroundKey].toEnum(WidgetBackground.TINTED),
        theme = this[ThemeKey].toEnum(WidgetTheme.FOLLOW_APP),
        opacity = this[OpacityKey] ?: 1f,
    )

/**
 * This widget's agenda options. A widget placed before per-widget options
 * existed has none stored, and keeps following the app-wide values in
 * [defaults] until it is configured.
 */
internal fun Preferences.agendaWidgetOptions(defaults: AgendaWidgetOptions) = AgendaWidgetOptions(
    range = parseAgendaRange(this[AgendaRangeKey], defaults.range),
    textSize = this[TextSizeKey].toEnum(defaults.textSize),
    showToday = this[ShowTodayKey] ?: defaults.showToday,
    pastDisplay = this[PastDisplayKey].toEnum(defaults.pastDisplay),
    style = this[AgendaStyleKey].toEnum(defaults.style),
    invertText = this[InvertTextKey] ?: defaults.invertText,
)

/** Whether this widget has agenda options of its own, rather than following the app's. */
internal val Preferences.hasAgendaOptions: Boolean get() = this[AgendaRangeKey] != null

internal fun MutablePreferences.putAppearance(appearance: WidgetAppearance) {
    this[BackgroundKey] = appearance.background.name
    this[ThemeKey] = appearance.theme.name
    this[OpacityKey] = appearance.opacity
}

internal fun MutablePreferences.putAgendaOptions(options: AgendaWidgetOptions) {
    this[AgendaRangeKey] = options.range.storageValue()
    this[TextSizeKey] = options.textSize.name
    this[ShowTodayKey] = options.showToday
    this[PastDisplayKey] = options.pastDisplay.name
    this[AgendaStyleKey] = options.style.name
    this[InvertTextKey] = options.invertText
}

/**
 * Calendars this widget leaves out, on top of the ones hidden app-wide. Stored
 * as the excluded set, so a calendar added later shows up by default.
 */
internal val Preferences.hiddenCalendars: Set<Long>
    get() = this[HiddenCalendarsKey]?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()

internal fun MutablePreferences.putHiddenCalendars(ids: Set<Long>) {
    this[HiddenCalendarsKey] = ids.map(Long::toString).toSet()
}

internal fun List<EventInstance>.withoutCalendars(hidden: Set<Long>): List<EventInstance> =
    if (hidden.isEmpty()) this else filterNot { it.calendarId in hidden }

internal suspend fun Context.widgetState(id: GlanceId): Preferences =
    getAppWidgetState(this, PreferencesGlanceStateDefinition, id)
