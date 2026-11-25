package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.OnboardingPrimaryButton
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

/**
 * The wizard's closing screen (#163): the flow ends on a full screen saying so
 * rather than dropping into the calendar mid-gesture, and the brand mark wears
 * the check that the last step earned.
 */
@Composable
internal fun DoneStep(
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
        hero = { BrandHero(HeroBadge.Done) },
        actions = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_done_button),
                onClick = onFinished,
            )
        },
        headline = stringResource(R.string.onboarding_done_title),
        supporting = stringResource(R.string.onboarding_done_body),
    )
}
