package de.jeanlucmakiola.calendula.widget.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.settings.pastEventDisplayLabelRes
import de.jeanlucmakiola.calendula.widget.AgendaWidgetOptions
import de.jeanlucmakiola.calendula.widget.WidgetAppearance
import de.jeanlucmakiola.calendula.widget.WidgetOption
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.WidgetTheme
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidgetContent
import de.jeanlucmakiola.calendula.widget.agenda.metricsFor
import de.jeanlucmakiola.calendula.widget.month.MonthWidgetContent
import de.jeanlucmakiola.calendula.widget.quickadd.QuickAddWidgetContent
import de.jeanlucmakiola.calendula.widget.date.DateWidgetContent
import de.jeanlucmakiola.calendula.widget.week.WeekStripWidgetContent
import de.jeanlucmakiola.calendula.widget.upcoming.UpcomingWidgetContent
import de.jeanlucmakiola.calendula.widget.today.TodayWidgetContent
import de.jeanlucmakiola.calendula.widget.countdown.CountdownOptions
import de.jeanlucmakiola.calendula.widget.countdown.CountdownWidgetContent
import de.jeanlucmakiola.calendula.widget.split.MonthDayWidgetContent
import de.jeanlucmakiola.calendula.widget.WidgetKind
import de.jeanlucmakiola.floret.components.GroupedChoiceRow
import de.jeanlucmakiola.floret.components.GroupedHint
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.GroupedSectionHeader
import de.jeanlucmakiola.floret.components.GroupedSwitchRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.glance.GlanceWidgetPreview
import de.jeanlucmakiola.floret.glance.WallpaperWindow
import de.jeanlucmakiola.floret.glance.WidgetBackground
import kotlin.math.roundToInt
import de.jeanlucmakiola.floret.components.GroupedSliderRow
import de.jeanlucmakiola.floret.components.InfoPopover
import de.jeanlucmakiola.calendula.widget.AgendaWidgetStyle
import java.text.NumberFormat
import de.jeanlucmakiola.floret.locale.currentLocale
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row

private val PreviewMaxHeight = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetConfigScreen(
    state: WidgetConfigUiState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onAppearance: (WidgetAppearance) -> Unit,
    onContent: (AgendaWidgetOptions) -> Unit,
    onCalendarShown: (Long, Boolean) -> Unit,
    onCountdown: (CountdownOptions) -> Unit,
    onLoadCountdownCandidates: () -> Unit,
) {
    val ready = state as? WidgetConfigUiState.Ready
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = ready?.let { stringResource(it.kind.label) }.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.event_edit_close))
                }
            },
            actions = {
                Button(
                    onClick = onSave,
                    enabled = ready != null && !ready.saving,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = surface),
        )
        when (state) {
            WidgetConfigUiState.Loading, WidgetConfigUiState.Missing -> Box(
                modifier = Modifier.fillMaxSize().background(surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            WidgetConfigUiState.Failed -> Column(
                modifier = Modifier.fillMaxSize().background(surface).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.widget_config_load_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.state_retry)) }
            }
            is WidgetConfigUiState.Ready -> {
                WallpaperWindow(color = surface) {
                    val maxSize = DpSize(maxWidth, PreviewMaxHeight)
                    when (val preview = state.preview) {
                        null -> Box(
                            modifier = Modifier.padding(vertical = 16.dp).height(maxSize.height / 2),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.previewFailed) PreviewFailed() else CircularProgressIndicator()
                        }
                        else -> GlanceWidgetPreview(
                            size = state.placedSize,
                            maxSize = maxSize,
                            preview,
                            state.appearance,
                            state.appTheme,
                            modifier = Modifier.padding(vertical = 16.dp),
                            placeholder = { CircularProgressIndicator() },
                            failure = { PreviewFailed() },
                        ) {
                            when (preview) {
                                is WidgetPreviewData.Agenda ->
                                    AgendaWidgetContent(preview.data, state.appearance, state.appTheme, preview = true)
                                is WidgetPreviewData.Month ->
                                    MonthWidgetContent(preview.source, preview.index, state.appearance, state.appTheme)
                                is WidgetPreviewData.MonthDay -> MonthDayWidgetContent(
                                    preview.data,
                                    preview.index,
                                    selected = null,
                                    appearance = state.appearance,
                                    appTheme = state.appTheme,
                                    preview = true,
                                )
                                is WidgetPreviewData.Countdown ->
                                    CountdownWidgetContent(preview.data, state.appearance, state.appTheme)
                                is WidgetPreviewData.Days -> when (state.kind) {
                                    WidgetKind.TODAY ->
                                        TodayWidgetContent(preview.data, state.appearance, state.appTheme, preview = true)
                                    WidgetKind.UPCOMING -> UpcomingWidgetContent(preview.data, state.appearance, state.appTheme)
                                    else -> WeekStripWidgetContent(preview.data, state.appearance, state.appTheme)
                                }
                                is WidgetPreviewData.Today -> if (state.kind == WidgetKind.DATE) {
                                    DateWidgetContent(preview.today, state.appearance, state.appTheme)
                                } else {
                                    QuickAddWidgetContent(preview.today, state.appearance, state.appTheme)
                                }
                            }
                        }
                    }
                }
                ConfigOptions(
                    state = state,
                    onAppearance = onAppearance,
                    onContent = onContent,
                    onCalendarShown = onCalendarShown,
                    onCountdown = onCountdown,
                    onLoadCountdownCandidates = onLoadCountdownCandidates,
                    modifier = Modifier
                        .weight(1f)
                        .background(surface),
                )
            }
        }
    }
}

@Composable
private fun PreviewFailed() {
    Text(
        text = stringResource(R.string.widget_config_preview_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ConfigOptions(
    state: WidgetConfigUiState.Ready,
    onAppearance: (WidgetAppearance) -> Unit,
    onContent: (AgendaWidgetOptions) -> Unit,
    onCalendarShown: (Long, Boolean) -> Unit,
    onCountdown: (CountdownOptions) -> Unit,
    onLoadCountdownCandidates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        GroupedSectionHeader(stringResource(R.string.settings_section_appearance))
        GroupedChoiceRow(
            title = stringResource(R.string.widget_config_background),
            options = WidgetBackground.entries,
            selected = state.appearance.background,
            label = { backgroundLabel(it) },
            onSelect = { onAppearance(state.appearance.copy(background = it)) },
            position = Position.Top,
        )
        val faded = state.appearance.background != WidgetBackground.TRANSPARENT
        val content = state.draft.content
        val classic = state.kind == WidgetKind.AGENDA && content.style == AgendaWidgetStyle.CLASSIC
        if (faded) {
            val opacity = state.appearance.opacity
            GroupedSliderRow(
                title = stringResource(R.string.widget_config_opacity),
                value = opacity,
                onValueChange = { onAppearance(state.appearance.copy(opacity = it)) },
                position = Position.Middle,
                valueLabel = currentLocale().let { locale ->
                    remember(opacity, locale) { NumberFormat.getPercentInstance(locale).format(opacity) }
                },
                valueRange = MIN_OPACITY..1f,
                steps = OPACITY_STEPS,
                titleAccessory = {
                    InfoPopover(
                        text = stringResource(
                            if (classic) R.string.widget_config_readability_hint_classic else R.string.widget_config_readability_hint,
                        ),
                        contentDescription = stringResource(R.string.widget_config_readability_info),
                    )
                },
            )
        }
        if (faded && classic) {
            GroupedRow(
                title = stringResource(R.string.widget_config_invert_text),
                summary = stringResource(R.string.widget_config_invert_text_summary),
                position = Position.Middle,
                trailing = {
                    Switch(checked = content.invertText, onCheckedChange = { onContent(content.copy(invertText = it)) })
                },
                onClick = { onContent(content.copy(invertText = !content.invertText)) },
            )
        }
        GroupedChoiceRow(
            title = stringResource(R.string.settings_theme),
            options = WidgetTheme.entries,
            selected = state.appearance.theme,
            label = { themeLabel(it) },
            onSelect = { onAppearance(state.appearance.copy(theme = it)) },
            position = Position.Bottom,
        )
        if (state.kind == WidgetKind.COUNTDOWN) {
            CountdownOptionsSection(
                options = state.draft.countdown,
                candidates = state.candidates,
                onChange = onCountdown,
                onLoadCandidates = onLoadCountdownCandidates,
            )
        }
        if (state.kind.options.isNotEmpty()) {
            ContentOptions(state = state, onChange = onContent, onCalendarShown = onCalendarShown)
        }
    }
}

@Composable
private fun ContentOptions(
    state: WidgetConfigUiState.Ready,
    onChange: (AgendaWidgetOptions) -> Unit,
    onCalendarShown: (Long, Boolean) -> Unit,
) {
    val options = state.draft.content
    var showStyle by rememberSaveable { mutableStateOf(false) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    var showSize by rememberSaveable { mutableStateOf(false) }
    var showPast by rememberSaveable { mutableStateOf(false) }
    var showCalendars by rememberSaveable { mutableStateOf(false) }

    // Rows in WidgetOption order, each shaped by where it lands among the kind's own.
    val rows = WidgetOption.entries.filter { it in state.kind.options }
    fun position(option: WidgetOption) = positionOf(rows.indexOf(option), rows.size)

    GroupedSectionHeader(stringResource(R.string.widget_config_content))
    rows.forEach { option ->
        when (option) {
            WidgetOption.STYLE -> GroupedRow(
                title = stringResource(R.string.widget_config_style),
                summary = agendaStyleLabel(options.style),
                position = position(option),
                onClick = { showStyle = true },
            )
            WidgetOption.RANGE -> GroupedRow(
                title = stringResource(R.string.widget_config_range),
                summary = agendaRangeLabel(options.range),
                position = position(option),
                onClick = { showRange = true },
            )
            WidgetOption.TEXT_SIZE -> GroupedRow(
                title = stringResource(R.string.widget_config_text_size),
                summary = widgetSizeLabel(options.textSize),
                position = position(option),
                onClick = { showSize = true },
            )
            WidgetOption.PAST_DISPLAY -> GroupedRow(
                title = stringResource(R.string.settings_past_events),
                summary = stringResource(pastEventDisplayLabelRes(options.pastDisplay)),
                position = position(option),
                onClick = { showPast = true },
            )
            WidgetOption.SHOW_TODAY -> GroupedSwitchRow(
                title = stringResource(R.string.settings_agenda_show_today),
                checked = options.showToday,
                onCheckedChange = { onChange(options.copy(showToday = it)) },
                position = position(option),
            )
            WidgetOption.CALENDARS -> GroupedRow(
                title = stringResource(R.string.widget_config_calendars),
                summary = calendarsSummary(state.calendars, state.draft.hiddenCalendars),
                position = position(option),
                onClick = { showCalendars = true },
            )
        }
    }

    if (showCalendars) {
        WidgetCalendarPicker(
            calendars = state.calendars,
            hidden = state.draft.hiddenCalendars,
            onSetShown = onCalendarShown,
            onDismiss = { showCalendars = false },
        )
    }

    if (showStyle) {
        OptionPicker(
            title = stringResource(R.string.widget_config_style),
            predictiveBack = true,
            options = AgendaWidgetStyle.entries,
            selected = options.style,
            label = { agendaStyleLabel(it) },
            onSelect = { onChange(options.copy(style = it)) },
            onDismiss = { showStyle = false },
        )
    }
    if (showRange) {
        AgendaRangePicker(
            title = stringResource(R.string.widget_config_range),
            description = stringResource(R.string.settings_agenda_widget_range_hint),
            selected = options.range,
            weekStart = state.weekStart,
            onSelect = { onChange(options.copy(range = it)) },
            onDismiss = { showRange = false },
        )
    }
    if (showSize) {
        OptionPicker(
            title = stringResource(R.string.widget_config_text_size),
            header = { GroupedHint(stringResource(R.string.settings_widget_size_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = WidgetSize.entries,
            selected = options.textSize,
            label = { widgetSizeLabel(it) },
            summary = { widgetSizeSummary(it) },
            onSelect = { onChange(options.copy(textSize = it)) },
            onDismiss = { showSize = false },
        )
    }
    if (showPast) {
        OptionPicker(
            title = stringResource(R.string.settings_past_events),
            header = { GroupedHint(stringResource(R.string.settings_past_events_hint), verticalPadding = 8.dp) },
            predictiveBack = true,
            options = PastEventDisplay.entries,
            selected = options.pastDisplay,
            label = { stringResource(pastEventDisplayLabelRes(it)) },
            onSelect = { onChange(options.copy(pastDisplay = it)) },
            onDismiss = { showPast = false },
        )
    }
}

@Composable
private fun agendaStyleLabel(style: AgendaWidgetStyle): String = stringResource(
    when (style) {
        AgendaWidgetStyle.CARDS -> R.string.widget_config_style_cards
        AgendaWidgetStyle.CLASSIC -> R.string.widget_config_style_classic
    },
)

/** The faintest fill the opacity slider allows, and its 10% steps between that and opaque. */
private const val MIN_OPACITY = 0.2f
private const val OPACITY_STEPS = 7

@Composable
internal fun backgroundLabel(background: WidgetBackground): String = stringResource(
    when (background) {
        WidgetBackground.SOLID -> R.string.widget_config_background_solid
        WidgetBackground.TINTED -> R.string.widget_config_background_tinted
        WidgetBackground.TRANSPARENT -> R.string.widget_config_background_transparent
    },
)

@Composable
internal fun themeLabel(theme: WidgetTheme): String = stringResource(
    when (theme) {
        WidgetTheme.FOLLOW_APP -> R.string.widget_config_theme_follow_app
        WidgetTheme.LIGHT -> R.string.settings_theme_light
        WidgetTheme.DARK -> R.string.settings_theme_dark
    },
)

/**
 * What a size step does, as a percentage of the smallest one's event text.
 * Derived from the widget's own [metricsFor] table rather than hardcoded.
 */
@Composable
private fun widgetSizeSummary(size: WidgetSize): String {
    val base = metricsFor(WidgetSize.SMALL).eventTitle.value
    val percent = (metricsFor(size).eventTitle.value / base * 100f).roundToInt()
    return stringResource(R.string.settings_widget_size_summary, percent)
}

@Composable
private fun widgetSizeLabel(size: WidgetSize): String = stringResource(
    when (size) {
        WidgetSize.SMALL -> R.string.settings_widget_size_small
        WidgetSize.MEDIUM -> R.string.settings_widget_size_medium
        WidgetSize.LARGE -> R.string.settings_widget_size_large
        WidgetSize.EXTRA_LARGE -> R.string.settings_widget_size_extra_large
    },
)
