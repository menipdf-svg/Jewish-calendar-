package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.month.descriptionRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.OptionRows
import de.jeanlucmakiola.floret.components.ViewPreviewFrame
import kotlinx.datetime.DayOfWeek

/**
 * The Month view style chooser (#38, #53).
 *
 * A live, scaled-down Month view sits above the options and changes as you pick
 * one, so the choice is made by looking rather than by reading "Continuous" and
 * guessing. Selecting therefore applies immediately and leaves the picker open —
 * closing on tap would hide the very thing the screen is for. Back exits, the
 * same as the App name picker, which stays open for the same reason.
 *
 * Under the preview sits the selected style's own one-line blurb, then the
 * family's standard picker shape: connected single-line grouped rows with a
 * tonal highlight and a check on the current one.
 */
@Composable
internal fun MonthViewStylePicker(
    selected: MonthViewStyle,
    weekStart: DayOfWeek,
    showStartTimes: Boolean,
    onSelect: (MonthViewStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = MonthViewStyle.entries
    FullScreenPicker(
        title = stringResource(R.string.settings_month_view_style),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        ViewPreviewFrame(
            selected = selected,
            label = "month-style-preview",
            modifier = Modifier.padding(vertical = 8.dp),
        ) { shown ->
            MonthStylePreview(
                style = shown,
                weekStart = weekStart,
                showStartTimes = showStartTimes,
                height = PREVIEW_HEIGHT,
            )
        }
        // The selected style's own blurb lives here, under its preview, rather
        // than on every row — the rows stay single-line and dense, and the words
        // describe exactly what is being shown above.
        GroupedHint(stringResource(selected.descriptionRes), verticalPadding = 8.dp)
        // Applies straight away; the preview above is the confirmation, so
        // there is nothing to dismiss for.
        OptionRows(
            options = options,
            selected = selected,
            label = { stringResource(it.labelRes) },
            onSelect = onSelect,
        )
    }
}

private val PREVIEW_HEIGHT = 280.dp
