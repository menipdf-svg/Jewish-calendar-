package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toJavaInstant

/**
 * The top bar while an event is selected (#141): says which, and when, with a
 * way out — the selection otherwise only shows on the grid and in the FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(selection: EventSelection) {
    val event = selection.event
    // Follows a move or resize, so the times stay readable under the finger.
    val preview by remember(selection) { derivedStateOf { selection.preview() } }
    val shown = preview ?: event
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val whenText = remember(shown.start, shown.end, use24Hour, locale) {
        formatSelectionWhen(shown, use24Hour, locale)
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = selection.dismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        if (selection.create != null) R.string.event_draft_discard else R.string.event_selection_close,
                    ),
                )
            }
        },
        title = {
            Column {
                Text(
                    text = event.title.ifBlank { stringResource(R.string.event_untitled) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // The header's own surface, as the view's bar has (#186).
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/**
 * "Fri, 9 Oct · 09:00–10:00", or with both days named for an event that runs
 * into another: "Fri, 9 Oct, 22:00 – Sat, 10 Oct, 02:00".
 */
private fun formatSelectionWhen(event: EventInstance, use24Hour: Boolean, locale: Locale): String {
    val zone = ZoneId.systemDefault()
    val start = event.start.toJavaInstant().atZone(zone)
    val end = event.end.toJavaInstant().atZone(zone)
    val day = localizedDateFormatter(locale, "EEEdMMM")
    val time = timeOfDayFormatter(use24Hour, locale)
    val endsSameDay = end.toLocalDate() == start.toLocalDate() ||
        (end.toLocalTime().toSecondOfDay() == 0 && end.toLocalDate() == start.toLocalDate().plusDays(1))
    return if (endsSameDay) {
        "${day.format(start)} · ${time.format(start)}–${time.format(end)}"
    } else {
        "${day.format(start)}, ${time.format(start)} – ${day.format(end)}, ${time.format(end)}"
    }
}
