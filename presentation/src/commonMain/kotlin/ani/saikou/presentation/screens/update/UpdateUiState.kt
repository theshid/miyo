package ani.saikou.presentation.screens.update

import ani.saikou.domain.model.update.UpdateArtifactRef
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest

/**
 * One screen, many steps. Sealed interface so the UI can do a single
 * exhaustive `when` and the VM can transition with `_state.update { … }`.
 *
 * `mandatory` is duplicated across the states that carry a manifest: it's
 * derived from the manifest + installed version at the check step and
 * never recomputed, so the UI can trust it without reading the manifest.
 */
sealed interface UpdateUiState {
    /** Nothing to show — the global dialog renders no chrome. */
    data object Hidden : UpdateUiState

    /** Initial check in flight. The dialog stays hidden until we know there's news. */
    data object Checking : UpdateUiState

    data class Available(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
    ) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : UpdateUiState {
        val fraction: Float?
            get() {
                val total = totalBytes ?: return null
                if (total <= 0L) return null
                return (bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
    }

    data class Verifying(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
    ) : UpdateUiState

    data class ReadyToInstall(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
        val artifact: UpdateArtifactRef,
    ) : UpdateUiState

    /**
     * Download is complete but the user has not granted "install from unknown
     * sources". Tapping the CTA opens the per-app settings screen; when the
     * user returns we re-check and transition back to [`ReadyToInstall`].
     */
    data class AwaitingInstallPermission(
        val manifest: UpdateManifest,
        val mandatory: Boolean,
        val artifact: UpdateArtifactRef,
    ) : UpdateUiState

    data class Failed(
        val manifest: UpdateManifest?,
        val mandatory: Boolean,
        val error: UpdateError,
        /**
         * When the failure happened after the APK was prepared (typically
         * [`UpdateError.InstallerLaunchFailed`]), [`UpdateViewModel.retry`]
         * re-launches the installer rather than redownloading. Null when
         * the failure happened upstream of [`UpdateProgress.Ready`].
         */
        val artifact: UpdateArtifactRef? = null,
    ) : UpdateUiState
}
