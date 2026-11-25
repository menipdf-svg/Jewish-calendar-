package de.jeanlucmakiola.calendula.widget.quickadd

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.PreferencesGlanceStateDefinition
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.loadToday
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetShape
import de.jeanlucmakiola.floret.glance.WidgetShapedIconButton
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.LocalDate

/**
 * 1×1 quick add (#435): one shaped "+" that opens the new-event editor on
 * today, with the same defaults as the app's own new-event button.
 */
class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Exact: the shape fills whatever square the launcher gives it.
    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.QUICK_ADD)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.QUICK_ADD)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadToday(), refreshStamp(context, id))
        provideContent {
            // The rollover moves the stamp, so the editor opens on the new day.
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadToday() }
            InAppLanguage {
                QuickAddWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class QuickAddWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

@Composable
internal fun QuickAddWidgetContent(today: LocalDate, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        val size = LocalSize.current
        val action = actionStartActivity(MainActivity.openCreateIntent(context, today))
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            WidgetShapedIconButton(
                shape = WidgetShape.SUNNY,
                icon = ImageProvider(R.drawable.ic_widget_add),
                contentDescription = context.getString(R.string.widget_new_event),
                action = action,
                color = GlanceTheme.colors.primary,
                iconTint = GlanceTheme.colors.onPrimary,
                size = (minOf(size.width, size.height) - 12.dp).coerceIn(36.dp, 120.dp),
            )
        }
    }
}
