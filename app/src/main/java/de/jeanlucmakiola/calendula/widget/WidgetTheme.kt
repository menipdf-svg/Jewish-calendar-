package de.jeanlucmakiola.calendula.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.glance.LocalContext
import androidx.glance.text.TextDecoration
import de.jeanlucmakiola.calendula.ui.theme.CalendulaDarkFallback
import de.jeanlucmakiola.calendula.ui.theme.CalendulaLightFallback
import de.jeanlucmakiola.floret.glance.FloretGlanceTheme
import de.jeanlucmakiola.floret.glance.WidgetSurface
import de.jeanlucmakiola.floret.glance.WidgetThemeMode
import de.jeanlucmakiola.floret.locale.AppLanguage
import de.jeanlucmakiola.floret.prefs.ThemeMode

/**
 * Glance equivalent of `CalendulaTheme`: Material You on API 31+, the app's
 * hand-tuned fallback schemes below that, in the light/dark [mode] the widget
 * was configured with (#292).
 */
@Composable
fun CalendulaGlanceTheme(mode: WidgetThemeMode, content: @Composable () -> Unit) {
    FloretGlanceTheme(
        lightScheme = CalendulaLightFallback,
        darkScheme = CalendulaDarkFallback,
        dynamicColor = true,
        mode = mode,
        content = content,
    )
}

/** A widget's root: its configured theme and background around [content]. */
@Composable
internal fun CalendulaWidgetRoot(appearance: WidgetAppearance, appTheme: ThemeMode, content: @Composable () -> Unit) {
    CalendulaGlanceTheme(appearance.theme.resolve(appTheme)) {
        WidgetSurface(background = appearance.background, opacity = appearance.opacity, content = content)
    }
}

/**
 * Glance's counterpart to the app's `declinedDecoration`: a declined invitation
 * reads the same on the home screen as it does inside the app (#180).
 */
fun glanceDeclinedDecoration(isCrossedOut: Boolean): TextDecoration =
    if (isCrossedOut) TextDecoration.LineThrough else TextDecoration.None

/** Static, so a language change recomposes the whole widget rather than letting unchanged parts skip. */
private val LocalWidgetLanguage = staticCompositionLocalOf<String?> { null }

/**
 * Draws [content] in the app's own language (#90): [LocalContext] and the
 * default locale follow it, which below API 33 nothing else does outside an
 * AppCompat activity. Read on every recomposition, so a redraw picks up a change.
 */
@Composable
internal fun InAppLanguage(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val tag = AppLanguage.currentTag(base)
    AppLanguage.applyDefault(base, tag)
    val localized = remember(base, tag) { AppLanguage.localized(base, tag) }
    CompositionLocalProvider(LocalContext provides localized, LocalWidgetLanguage provides tag, content = content)
}
