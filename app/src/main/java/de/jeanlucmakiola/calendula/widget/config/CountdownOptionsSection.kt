package de.jeanlucmakiola.calendula.widget.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.dateZone
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.PickerSearchField
import de.jeanlucmakiola.calendula.widget.countdown.CountdownMode
import de.jeanlucmakiola.calendula.widget.countdown.CountdownOptions
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedChoiceRow
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.GroupedSectionHeader
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime

/** What the countdown widget counts to (#439): the next event, or one picked from a searchable list. */
@Composable
internal fun CountdownOptionsSection(
    options: CountdownOptions,
    candidates: CountdownCandidates,
    onChange: (CountdownOptions) -> Unit,
    onLoadCandidates: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    GroupedSectionHeader(stringResource(R.string.widget_config_countdown))
    GroupedChoiceRow(
        title = stringResource(R.string.widget_config_countdown),
        options = CountdownMode.entries,
        selected = options.mode,
        label = {
            stringResource(
                when (it) {
                    CountdownMode.NEXT -> R.string.widget_config_countdown_next
                    CountdownMode.PINNED -> R.string.widget_config_countdown_pinned
                },
            )
        },
        onSelect = { mode ->
            onChange(options.copy(mode = mode))
            if (mode == CountdownMode.PINNED && options.eventId == null) picking = true
        },
        position = if (options.mode == CountdownMode.PINNED) Position.Top else Position.Alone,
    )
    when (options.mode) {
        CountdownMode.NEXT -> GroupedHint(stringResource(R.string.widget_config_countdown_next_hint), verticalPadding = 8.dp)
        CountdownMode.PINNED -> GroupedRow(
            title = stringResource(R.string.widget_config_countdown_event),
            summary = options.title ?: stringResource(R.string.widget_config_countdown_event_none),
            position = Position.Bottom,
            onClick = { picking = true },
        )
    }
    if (picking) {
        LaunchedEffect(Unit) { onLoadCandidates() }
        CountdownEventPicker(
            candidates = candidates,
            selectedEventId = options.eventId,
            onRetry = onLoadCandidates,
            onSelect = { candidate ->
                onChange(options.copy(mode = CountdownMode.PINNED, eventId = candidate.event.eventId, title = candidate.event.title))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CountdownEventPicker(
    candidates: CountdownCandidates,
    selectedEventId: Long?,
    onRetry: () -> Unit,
    onSelect: (CountdownCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    FullScreenPicker(
        title = stringResource(R.string.widget_config_countdown_event),
        onDismiss = onDismiss,
        predictiveBack = true,
        scrollable = false,
    ) {
        PickerSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.widget_config_countdown_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (candidates) {
            CountdownCandidates.Idle, CountdownCandidates.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            CountdownCandidates.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PickerMessage(stringResource(R.string.widget_config_countdown_load_failed))
                FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.state_retry))
                }
            }
            is CountdownCandidates.Ready -> {
                val locale = currentLocale()
                val matches = remember(candidates, query) {
                    val needle = query.trim()
                    if (needle.isEmpty()) candidates.events
                    else candidates.events.filter { it.event.title.contains(needle, ignoreCase = true) }
                }
                if (matches.isEmpty()) {
                    PickerMessage(
                        stringResource(R.string.widget_config_countdown_empty),
                        Modifier.fillMaxWidth().padding(24.dp),
                    )
                } else {
                    val zone = remember { TimeZone.currentSystemDefault() }
                    val formatter = remember(locale) { localizedDateFormatter(locale, "EEEdMMMy") }
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                        itemsIndexed(matches, key = { _, it -> it.event.eventId }) { index, candidate ->
                            val date = formatter.format(candidate.event.start.toLocalDateTime(candidate.event.dateZone(zone)).date.toJavaLocalDate())
                            GroupedRow(
                                title = candidate.event.title.ifBlank { stringResource(R.string.event_untitled) },
                                summary = if (candidate.repeats) stringResource(R.string.widget_config_countdown_repeats, date) else date,
                                position = positionOf(index, matches.size),
                                selected = candidate.event.eventId == selectedEventId,
                                minHeight = 56.dp,
                                leading = { CalendarColorChip(candidate.event.color) },
                                onClick = { onSelect(candidate) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
