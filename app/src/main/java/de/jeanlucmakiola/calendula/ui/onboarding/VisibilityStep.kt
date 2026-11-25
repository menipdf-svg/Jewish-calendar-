package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.OnboardingPrimaryButton
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

/**
 * Wizard step for the one-time notice that Calendula now follows the device's
 * own per-calendar visibility and some of yours are switched off (#75, #163).
 *
 * Nothing to decide here — the app does not switch those calendars back on,
 * since that would un-hide them everywhere else too.
 */
@Composable
internal fun VisibilityStep(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.VisibilityOff) },
        actions = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_visibility_button),
                onClick = onFinished,
            )
        },
        headline = stringResource(R.string.calendars_visibility_notice_title),
        supporting = stringResource(R.string.calendars_visibility_notice_message),
    )
}
