package de.jeanlucmakiola.calendula.widget.date

import de.jeanlucmakiola.calendula.widget.InAppLanguage
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.calendula.widget.previewSizeOf
import de.jeanlucmakiola.calendula.widget.provideSamplePreview
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.CalendulaWidgetRoot
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.loadToday
import de.jeanlucmakiola.calendula.widget.widgetAppearance
import de.jeanlucmakiola.calendula.widget.widgetLocale
import de.jeanlucmakiola.floret.glance.WidgetData
import de.jeanlucmakiola.floret.glance.WidgetShape
import de.jeanlucmakiola.floret.glance.WidgetShapedBox
import de.jeanlucmakiola.floret.glance.currentRefreshStamp
import de.jeanlucmakiola.floret.glance.refreshStamp
import de.jeanlucmakiola.floret.glance.wallpaperBacking
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import de.jeanlucmakiola.floret.prefs.ThemeMode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import java.time.format.TextStyle as JavaTextStyle

/** 1×1 date (#376): today's weekday above its number on an expressive shape; opens today. */
class DateWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override val previewSizeMode = previewSizeOf(WidgetKind.DATE)

    override suspend fun providePreview(context: Context, widgetCategory: Int) =
        provideSamplePreview(context, WidgetKind.DATE)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = WidgetData(context.loadToday(), refreshStamp(context, id))
        provideContent {
            val loaded = widgetData.current(currentRefreshStamp()) { context.loadToday() }
            InAppLanguage {
                DateWidgetContent(loaded.content, currentState<Preferences>().widgetAppearance, loaded.appTheme)
            }
        }
    }
}

class DateWidgetReceiver : CalendulaWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateWidget()
}

private val WeekdayLine = 18.dp

@Composable
internal fun DateWidgetContent(today: LocalDate, appearance: WidgetAppearance, appTheme: ThemeMode) {
    CalendulaWidgetRoot(appearance, appTheme) {
        val context = LocalContext.current
        val size = LocalSize.current
        val fontScale = context.resources.configuration.fontScale
        val side = (minOf(size.width, size.height - WeekdayLine * fontScale) - 8.dp).coerceIn(32.dp, 120.dp)
        val label = localizedDateFormatter(widgetLocale(), "EEEEdMMMM").format(today.toJavaLocalDate())
        val weekday = TextStyle(
            color = GlanceTheme.colors.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(MainActivity.openDateIntent(context, today, CalendarView.Day)))
                .semantics { contentDescription = label },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val name = today.dayOfWeek.toJavaDayOfWeek().getDisplayName(JavaTextStyle.SHORT, widgetLocale())
            val backing = GlanceModifier.wallpaperBacking(radius = 8.dp, horizontalPadding = 6.dp)
            Text(text = name.uppercase(widgetLocale()), maxLines = 1, style = weekday, modifier = backing)
            Spacer(GlanceModifier.height(2.dp))
            WidgetShapedBox(shape = WidgetShape.COOKIE, color = GlanceTheme.colors.primary, size = side) {
                Text(
                    text = today.day.toString(),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onPrimary,
                        // Sized to the shape, so held against the font scale.
                        fontSize = (side.value * 0.45f / fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
