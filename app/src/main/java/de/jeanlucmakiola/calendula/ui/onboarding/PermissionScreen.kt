package de.jeanlucmakiola.calendula.ui.onboarding

import de.jeanlucmakiola.floret.components.BenefitRow
import de.jeanlucmakiola.floret.components.OnboardingPrimaryButton
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R

private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

@Composable
fun PermissionScreen(
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
) {
    var denied by rememberSaveable { mutableStateOf(false) }

    // READ and WRITE are requested together (one system dialog — same
    // permission group), but only READ gates the app: declining write keeps
    // Calendula usable read-only.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.READ_CALENDAR] == true) {
            onGranted()
        } else {
            denied = true
        }
    }

    if (denied) {
        DeniedContent(
            onRetry = {
                denied = false
                launcher.launch(CALENDAR_PERMISSIONS)
            },
            progress = progress,
            modifier = modifier,
        )
    } else {
        RationaleContent(
            onRequest = { launcher.launch(CALENDAR_PERMISSIONS) },
            progress = progress,
            modifier = modifier,
        )
    }
}

@Composable
private fun RationaleContent(
    onRequest: () -> Unit,
    progress: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        topSpacing = OnboardingSpace.lg,
        hero = { BrandHero() },
        actions = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.permission_request_button),
                onClick = onRequest,
                trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
            )
        },
        headline = stringResource(R.string.permission_rationale_title),
        supporting = stringResource(R.string.permission_rationale_body),
    ) {
        Spacer(Modifier.height(OnboardingSpace.lg))

        BenefitRow(
            icon = Icons.Filled.CalendarMonth,
            title = stringResource(R.string.permission_benefit_sync_title),
            body = stringResource(R.string.permission_benefit_sync_body),
        )
        Spacer(Modifier.height(OnboardingSpace.sm))
        BenefitRow(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.permission_benefit_private_title),
            body = stringResource(R.string.permission_benefit_private_body),
        )
    }
}

@Composable
private fun DeniedContent(
    onRetry: () -> Unit,
    progress: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        hero = { BrandHero(HeroBadge.Denied) },
        actions = {
            OnboardingPrimaryButton(
                text = stringResource(R.string.permission_open_settings_button),
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                },
            )
            TextButton(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.permission_retry_button))
            }
        },
        headline = stringResource(R.string.permission_denied_title),
        supporting = stringResource(R.string.permission_denied_body),
    )
}
