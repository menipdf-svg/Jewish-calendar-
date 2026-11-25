package de.jeanlucmakiola.calendula.widget.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.SettingsKeys
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.ui.theme.AppFontSettings
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme
import de.jeanlucmakiola.calendula.ui.theme.WindowThemeCache
import de.jeanlucmakiola.calendula.ui.theme.calendulaTypography
import de.jeanlucmakiola.calendula.ui.theme.resolveFontFamily
import de.jeanlucmakiola.floret.prefs.ThemeMode
import de.jeanlucmakiola.floret.prefs.get
import de.jeanlucmakiola.floret.prefs.isDark
import de.jeanlucmakiola.floret.prefs.isPureBlack
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A widget's config screen (#292): opened by the launcher when the widget is
 * placed, from its "Edit widget" action on Android 12+, and from the app's
 * widget settings. Shows over the wallpaper, so a transparent widget previews
 * against the real home screen.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {

    @Inject lateinit var settingsPrefs: SettingsPrefs

    private val viewModel: WidgetConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = viewModel.appWidgetId
        // Backing out of a fresh placement leaves no unconfigured widget behind.
        setResult(RESULT_CANCELED, result(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        WidgetConfigEvent.SAVED -> {
                            setResult(RESULT_OK, result(appWidgetId))
                            finish()
                        }
                        WidgetConfigEvent.SAVE_FAILED ->
                            Toast.makeText(this@WidgetConfigActivity, R.string.widget_config_save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val fontSettings = settingsPrefs.data.map {
            AppFontSettings(
                brand = it[SettingsKeys.brandFont],
                plain = it[SettingsKeys.plainFont],
                brandStamp = it[SettingsKeys.brandFontStamp],
                plainStamp = it[SettingsKeys.plainFontStamp],
            )
        }
        // Seeded from MainActivity's mirrored copy, so an app theme override
        // shows from the first frame.
        val cachedTheme = WindowThemeCache.read(this) ?: ThemeMode.SYSTEM
        val cachedDynamicColor = WindowThemeCache.readDynamicColor(this) ?: true
        setContent {
            val themeMode by settingsPrefs.themeMode.collectAsStateWithLifecycle(cachedTheme)
            val dynamicColor by settingsPrefs.dynamicColor.collectAsStateWithLifecycle(cachedDynamicColor)
            val fonts by fontSettings.collectAsStateWithLifecycle(initialValue = null)
            val context = LocalContext.current
            // Nothing until the font choice is known, rather than a frame in the default one.
            val typography = remember(fonts, context) {
                fonts?.let {
                    calendulaTypography(
                        brand = resolveFontFamily(it.brand, FontRole.BRAND, context),
                        plain = resolveFontFamily(it.plain, FontRole.PLAIN, context),
                    )
                }
            } ?: return@setContent
            CalendulaTheme(
                darkTheme = themeMode.isDark(isSystemInDarkTheme()),
                dynamicColor = dynamicColor,
                pureBlack = themeMode.isPureBlack,
                typography = typography,
            ) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                if (state is WidgetConfigUiState.Missing) LaunchedEffect(Unit) { finish() }
                WidgetConfigScreen(
                    state = state,
                    onClose = ::finish,
                    onSave = viewModel::save,
                    onRetry = viewModel::retry,
                    onAppearance = viewModel::setAppearance,
                    onContent = viewModel::setContent,
                    onCalendarShown = viewModel::setCalendarShown,
                    onCountdown = viewModel::setCountdown,
                    onLoadCountdownCandidates = viewModel::loadCountdownCandidates,
                )
            }
        }
    }

    private fun result(appWidgetId: Int) = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        /** Opens placed widget [appWidgetId]'s config screen from inside the app. */
        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}
