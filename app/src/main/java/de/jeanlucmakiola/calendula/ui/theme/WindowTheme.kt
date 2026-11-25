package de.jeanlucmakiola.calendula.ui.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.edit
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.prefs.ThemeMode

/**
 * A synchronous copy of the theme and dynamic-colour choices, for the window
 * backdrop and the first Compose frame. The real setting lives in DataStore, which only
 * answers asynchronously — too late for onCreate, so a Light, Dark or Black
 * override flashed the night-qualifier backdrop on a cold start or a
 * recreation (#181). MainActivity mirrors every stored value here and reads it
 * back before setContent.
 */
internal object WindowThemeCache {

    private const val FILE = "window_theme"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    /** The last mirrored theme choice, or null before the first mirror. */
    fun read(context: Context): ThemeMode? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }

    /** The last mirrored dynamic-colour choice, or null before the first mirror. */
    fun readDynamicColor(context: Context): Boolean? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_DYNAMIC_COLOR)) prefs.getBoolean(KEY_DYNAMIC_COLOR, true) else null
    }

    fun write(context: Context, mode: ThemeMode, dynamicColor: Boolean) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_THEME_MODE, null) == mode.name &&
            prefs.contains(KEY_DYNAMIC_COLOR) && prefs.getBoolean(KEY_DYNAMIC_COLOR, true) == dynamicColor
        ) {
            return
        }
        prefs.edit {
            putString(KEY_THEME_MODE, mode.name)
            putBoolean(KEY_DYNAMIC_COLOR, dynamicColor)
        }
    }
}

/**
 * The window backdrop matching this theme choice's Compose background, or null
 * for [ThemeMode.SYSTEM], whose night-qualified theme default already matches.
 */
@ColorRes
internal fun ThemeMode.windowBackgroundRes(): Int? = when (this) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> R.color.window_background_light
    ThemeMode.DARK -> R.color.window_background_dark
    ThemeMode.BLACK -> R.color.window_background_black
}
