package ani.saikou.sharedui.screens.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.util.formatBytes
import ani.saikou.presentation.screens.update.UpdateUiState

/**
 * Top-level entry. Returns a renderless overlay for [`UpdateUiState.Hidden`]
 * and [`UpdateUiState.Checking`] (the dialog reveals itself once we know
 * there's an update to show). All other states render a modal dialog.
 *
 * Mandatory states wire [`DialogProperties`] to dismissOnBackPress=false +
 * dismissOnClickOutside=false. The Android-level "you can still press home"
 * caveat is documented in docs/self-update-and-release.md.
 */
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    val visible = state !is UpdateUiState.Hidden && state !is UpdateUiState.Checking
    if (!visible) return

    val mandatory = state.isMandatory()
    Dialog(
        onDismissRequest = { if (!mandatory) onLater() },
        properties =
            DialogProperties(
                dismissOnBackPress = !mandatory,
                dismissOnClickOutside = !mandatory,
                usePlatformDefaultWidth = true,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UpdateDialogContent(state)
                UpdateDialogActions(
                    state = state,
                    mandatory = mandatory,
                    onUpdate = onUpdate,
                    onLater = onLater,
                    onInstall = onInstall,
                    onGrantPermission = onGrantPermission,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun UpdateDialogContent(state: UpdateUiState) {
    when (state) {
        is UpdateUiState.Available -> AvailableBody(state)
        is UpdateUiState.Downloading -> DownloadingBody(state)
        is UpdateUiState.Verifying -> VerifyingBody(state)
        is UpdateUiState.ReadyToInstall -> ReadyBody(state)
        is UpdateUiState.AwaitingInstallPermission -> PermissionBody(state)
        is UpdateUiState.Failed -> FailedBody(state)
        UpdateUiState.Checking, UpdateUiState.Hidden -> Unit
    }
}

@Composable
private fun AvailableBody(state: UpdateUiState.Available) {
    DialogHeader(
        title = "Update available",
        versionName = state.manifest.versionName,
        mandatory = state.mandatory,
    )
    ReleaseNotes(state.manifest.releaseNotes)
}

@Composable
private fun DownloadingBody(state: UpdateUiState.Downloading) {
    DialogHeader(
        title = "Downloading update",
        versionName = state.manifest.versionName,
        mandatory = state.mandatory,
    )
    Spacer(Modifier.height(4.dp))
    val fraction = state.fraction
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    val total = state.totalBytes
    val sizeText =
        if (total != null && total > 0L) {
            "${formatBytes(state.bytesRead)} / ${formatBytes(total)}"
        } else {
            formatBytes(state.bytesRead)
        }
    Text(
        text = sizeText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun VerifyingBody(state: UpdateUiState.Verifying) {
    DialogHeader(
        title = "Verifying update",
        versionName = state.manifest.versionName,
        mandatory = state.mandatory,
    )
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(
        text = "Checking signature and integrity…",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReadyBody(state: UpdateUiState.ReadyToInstall) {
    DialogHeader(
        title = "Ready to install",
        versionName = state.manifest.versionName,
        mandatory = state.mandatory,
    )
    Text(
        text = "Tap Install to launch Android's installer.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionBody(state: UpdateUiState.AwaitingInstallPermission) {
    DialogHeader(
        title = "Allow installation",
        versionName = state.manifest.versionName,
        mandatory = state.mandatory,
    )
    Text(
        text =
            "Android needs your permission to install updates from Miyo. " +
                "We'll send you to the per-app setting — flip the toggle and return.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FailedBody(state: UpdateUiState.Failed) {
    DialogHeader(
        title = "Update failed",
        versionName = state.manifest?.versionName,
        mandatory = state.mandatory,
    )
    Text(
        text = friendlyError(state.error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DialogHeader(
    title: String,
    versionName: String?,
    mandatory: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (versionName != null) {
            Text(
                text = "Miyo $versionName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (mandatory) {
            Text(
                text = "Required update",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ReleaseNotes(notes: String) {
    if (notes.isBlank()) return
    val scroll = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(scroll),
    ) {
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UpdateDialogActions(
    state: UpdateUiState,
    mandatory: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (!mandatory && state is UpdateUiState.Available) {
            TextButton(onClick = onLater) { Text("Later") }
        }
        if (!mandatory && state is UpdateUiState.Failed) {
            TextButton(onClick = onLater) { Text("Later") }
        }
        when (state) {
            is UpdateUiState.Available -> OutlinedButton(onClick = onUpdate) { Text("Update") }
            is UpdateUiState.ReadyToInstall -> OutlinedButton(onClick = onInstall) { Text("Install") }
            is UpdateUiState.AwaitingInstallPermission ->
                OutlinedButton(onClick = onGrantPermission) { Text("Allow install") }
            is UpdateUiState.Failed -> OutlinedButton(onClick = onRetry) { Text("Retry") }
            is UpdateUiState.Downloading, is UpdateUiState.Verifying -> Unit
            UpdateUiState.Checking, UpdateUiState.Hidden -> Unit
        }
    }
}

private fun UpdateUiState.isMandatory(): Boolean =
    when (this) {
        is UpdateUiState.Available -> mandatory
        is UpdateUiState.Downloading -> mandatory
        is UpdateUiState.Verifying -> mandatory
        is UpdateUiState.ReadyToInstall -> mandatory
        is UpdateUiState.AwaitingInstallPermission -> mandatory
        is UpdateUiState.Failed -> mandatory
        UpdateUiState.Checking, UpdateUiState.Hidden -> false
    }

private fun friendlyError(error: UpdateError): String =
    when (error) {
        is UpdateError.NetworkUnavailable -> "Couldn't reach the update server. Check your connection and try again."
        is UpdateError.ManifestUnreachable -> "The update server is temporarily unavailable. Try again in a moment."
        is UpdateError.ManifestMalformed -> "The update server returned unexpected data. Try again later."
        is UpdateError.DownloadFailed -> "The download was interrupted. Try again."
        UpdateError.ChecksumMismatch -> "The downloaded update was corrupted. Try again."
        UpdateError.PackageMismatch -> "The downloaded file isn't a Miyo update."
        UpdateError.VersionMismatch -> "The downloaded update didn't match the announced version."
        UpdateError.SignerMismatch -> "The downloaded update isn't signed by Miyo's developer. Refusing to install."
        is UpdateError.InstallerLaunchFailed -> "Couldn't open the system installer. Make sure you have enough storage and try again."
    }
