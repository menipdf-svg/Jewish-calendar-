package de.jeanlucmakiola.calendula.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.qs.NewEventTileService
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.GroupedSectionHeader
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.calendula.widget.config.PlacedWidget
import de.jeanlucmakiola.calendula.widget.config.WidgetConfigActivity
import de.jeanlucmakiola.calendula.widget.WidgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.jeanlucmakiola.calendula.widget.config.backgroundLabel
import de.jeanlucmakiola.calendula.widget.config.placedWidgets
import de.jeanlucmakiola.calendula.widget.config.themeLabel

/**
 * Widgets & tiles (#69): the placed home-screen widgets, each opening its own
 * config screen (#292), and the Quick Settings tile shortcut.
 */
@Composable
internal fun WidgetsScreen(
    onBack: () -> Unit,
    visible: Boolean = true,
) {
    CollapsingScaffold(
        title = stringResource(R.string.settings_section_widgets),
        onBack = onBack,
        predictiveBack = true,
        visible = visible,
    ) {
        GroupedHint(
            stringResource(
                // The launcher's "Edit widget" action only exists from Android 12.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.settings_widgets_hint
                else R.string.settings_widgets_hint_legacy,
            ),
        )
        GroupedSectionHeader(stringResource(R.string.settings_widgets_placed))
        PlacedWidgetRows()

        // The add-tile prompt is API 33+; below that the tile is still
        // addable from the QS editor, so the row just doesn't appear.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(24.dp))
            QuickSettingsTileRow()
        }
    }
}

@Composable
private fun PlacedWidgetRows() {
    val context = LocalContext.current
    // Read on every resume, the first included, so a widget added, removed or
    // edited meanwhile shows up.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val widgets by produceState<List<PlacedWidget>?>(initialValue = null, refresh) {
        if (refresh > 0) value = withContext(Dispatchers.IO) { context.placedWidgets() }
    }
    val placed = widgets ?: return
    if (placed.isEmpty()) {
        GroupedRow(
            title = stringResource(R.string.settings_widgets_none),
            position = Position.Alone,
            dimmed = true,
        )
        return
    }
    val kindCounts = placed.groupingBy { it.kind }.eachCount()
    val kindSeen = mutableMapOf<WidgetKind, Int>()
    placed.forEachIndexed { index, widget ->
        val number = kindSeen.merge(widget.kind, 1, Int::plus)!!
        val label = stringResource(widget.kind.label)
        GroupedRow(
            title = if (kindCounts.getValue(widget.kind) > 1) stringResource(R.string.widget_numbered, label, number) else label,
            summary = stringResource(
                R.string.widget_appearance_summary,
                backgroundLabel(widget.appearance.background),
                themeLabel(widget.appearance.theme),
            ),
            position = positionOf(index, placed.size),
            onClick = { context.startActivity(WidgetConfigActivity.intent(context, widget.appWidgetId)) },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun QuickSettingsTileRow() {
    val context = LocalContext.current
    GroupedRow(
        title = stringResource(R.string.settings_qs_tile),
        summary = stringResource(R.string.settings_qs_tile_hint),
        position = Position.Alone,
        onClick = { requestAddQsTile(context) },
    )
}

/**
 * Ask the system to add the "New event" Quick Settings tile (API 33+). The OS
 * shows its own confirmation dialog and handles the already-added case, so no
 * result handling is needed here.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddQsTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, NewEventTileService::class.java),
        context.getString(R.string.qs_tile_new_event_label),
        Icon.createWithResource(context, R.drawable.ic_qs_new_event),
        context.mainExecutor,
    ) { /* result code unused — the system surfaces its own feedback */ }
}
