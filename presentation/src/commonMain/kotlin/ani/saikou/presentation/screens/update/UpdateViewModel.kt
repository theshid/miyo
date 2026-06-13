package ani.saikou.presentation.screens.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.update.UpdateAvailability
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.model.update.UpdateProgress
import ani.saikou.domain.repository.UpdateRepository
import ani.saikou.domain.usecase.update.CheckForUpdateUseCase
import ani.saikou.domain.usecase.update.CleanupUpdateArtifactsUseCase
import ani.saikou.domain.usecase.update.DownloadUpdateUseCase
import ani.saikou.domain.usecase.update.InstallUpdateUseCase
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drives the global self-update dialog. Process-scoped singleton (registered
 * via `viewModelOf`) so the check fires exactly once per launch regardless of
 * how many times [`SaikouApp`] recomposes.
 *
 * The controller never touches Android types — it consumes [`UpdateRepository`]
 * for everything platform-specific (PackageManager, FileProvider, settings
 * intent). Repo is read directly to query install-permission state because
 * that's a synchronous predicate rather than a use case verb.
 */
class UpdateViewModel(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val downloadUpdate: DownloadUpdateUseCase,
    private val installUpdate: InstallUpdateUseCase,
    private val cleanupArtifacts: CleanupUpdateArtifactsUseCase,
    private val repository: UpdateRepository,
    private val logger: Logger,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Hidden)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var checkStarted: Boolean = false
    private var downloadJob: Job? = null

    /**
     * Called by [`SaikouApp`] on every connectivity-up tick. Internally
     * deduplicates: once a check has completed successfully (either UpToDate
     * or Available), subsequent calls are no-ops. Transient failures
     * (network unreachable / manifest 5xx) leave the guard OPEN so a later
     * connectivity event — e.g. when the user exits a captive portal — can
     * retry without restarting the process.
     */
    fun checkOnce() {
        if (checkStarted) return
        checkStarted = true
        _uiState.value = UpdateUiState.Checking
        viewModelScope.launch {
            try {
                val availability = checkForUpdate()
                when (availability) {
                    is UpdateAvailability.UpToDate -> {
                        _uiState.value = UpdateUiState.Hidden
                        runCatching { cleanupArtifacts(null) }
                    }
                    is UpdateAvailability.Available -> {
                        _uiState.value = UpdateUiState.Available(availability.manifest, availability.mandatory)
                        runCatching { cleanupArtifacts(availability.manifest) }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: UpdateError) {
                logger.reportWarning(
                    area = LOG_AREA,
                    method = "checkOnce",
                    message = "update check failed: ${e::class.simpleName}",
                )
                _uiState.value = UpdateUiState.Hidden
                if (e.isTransient()) checkStarted = false
            } catch (e: Throwable) {
                logger.reportError(LOG_AREA, "checkOnce", e)
                _uiState.value = UpdateUiState.Hidden
                checkStarted = false
            }
        }
    }

    private fun UpdateError.isTransient(): Boolean =
        when (this) {
            is UpdateError.NetworkUnavailable,
            is UpdateError.ManifestUnreachable,
            is UpdateError.DownloadFailed,
            is UpdateError.InstallerLaunchFailed,
            -> true
            is UpdateError.ManifestMalformed,
            UpdateError.ChecksumMismatch,
            UpdateError.PackageMismatch,
            UpdateError.VersionMismatch,
            UpdateError.SignerMismatch,
            -> false
        }

    /** User tapped "Update". Begin the streaming download. */
    fun startDownload() {
        val current = _uiState.value
        val manifest =
            (current as? UpdateUiState.Available)?.manifest
                ?: (current as? UpdateUiState.Failed)?.manifest
                ?: return
        val mandatory =
            when (current) {
                is UpdateUiState.Available -> current.mandatory
                is UpdateUiState.Failed -> current.mandatory
                else -> false
            }
        downloadJob?.cancel()
        downloadJob =
            viewModelScope.launch {
                beginDownload(manifest, mandatory)
            }
    }

    /**
     * Retry from a failed state. If the APK was already prepared before the
     * failure (installer launch failed), re-run the install path so we don't
     * redownload bytes the user already paid for. Otherwise re-run the
     * download. With no manifest cached at all, fall back to a fresh check.
     */
    fun retry() {
        val current = _uiState.value as? UpdateUiState.Failed ?: return
        val artifact = current.artifact
        val manifest = current.manifest
        when {
            artifact != null && manifest != null -> {
                _uiState.value = UpdateUiState.ReadyToInstall(manifest, current.mandatory, artifact)
                install()
            }
            manifest != null -> {
                downloadJob?.cancel()
                downloadJob =
                    viewModelScope.launch {
                        beginDownload(manifest, current.mandatory)
                    }
            }
            else -> {
                // No manifest cached — back out and re-check from scratch.
                checkStarted = false
                checkOnce()
            }
        }
    }

    /** Dismiss for an optional update. No-op when the current update is mandatory. */
    fun dismiss() {
        val current = _uiState.value
        val mandatory =
            when (current) {
                is UpdateUiState.Available -> current.mandatory
                is UpdateUiState.Downloading -> current.mandatory
                is UpdateUiState.Verifying -> current.mandatory
                is UpdateUiState.ReadyToInstall -> current.mandatory
                is UpdateUiState.AwaitingInstallPermission -> current.mandatory
                is UpdateUiState.Failed -> current.mandatory
                UpdateUiState.Checking, UpdateUiState.Hidden -> false
            }
        if (mandatory) return
        downloadJob?.cancel()
        _uiState.value = UpdateUiState.Hidden
    }

    /** User tapped "Install" on the ready state. */
    fun install() {
        val ready = _uiState.value as? UpdateUiState.ReadyToInstall ?: return
        if (!repository.isInstallPermissionGranted()) {
            _uiState.value =
                UpdateUiState.AwaitingInstallPermission(
                    manifest = ready.manifest,
                    mandatory = ready.mandatory,
                    artifact = ready.artifact,
                )
            return
        }
        viewModelScope.launch {
            launchInstaller(ready.manifest, ready.mandatory, ready.artifact)
        }
    }

    /** User tapped the permission CTA — open the system settings page. */
    fun openInstallPermissionSettings() {
        if (_uiState.value !is UpdateUiState.AwaitingInstallPermission) return
        // Leave state as-is; the next onForegrounded() tick re-checks and
        // either advances to install or stays parked.
        repository.openInstallPermissionSettings()
    }

    /**
     * Called by the host UI on every onResume(). Two purposes:
     *
     * 1. If the user just returned from clearing a captive portal page, the
     *    underlying connectivity flow won't re-emit "true" (the boolean
     *    hasn't changed). Calling [`checkOnce`] here lets a previously
     *    transient-failed check retry — the VM's own guard makes successful
     *    runs sticky.
     * 2. If the user just returned from granting the unknown-sources
     *    permission, advance straight to launching the installer.
     *
     * Transitions out of [`AwaitingInstallPermission`] FIRST so a subsequent
     * resume (e.g. after the user cancels Android's installer screen) does
     * not re-fire the installer intent on every onResume.
     */
    fun onForegrounded() {
        // Captive-portal recovery: VM internally dedupes via `checkStarted`,
        // so this is a no-op once a check has completed successfully.
        checkOnce()

        val awaiting = _uiState.value as? UpdateUiState.AwaitingInstallPermission ?: return
        if (!repository.isInstallPermissionGranted()) return
        _uiState.value =
            UpdateUiState.ReadyToInstall(
                manifest = awaiting.manifest,
                mandatory = awaiting.mandatory,
                artifact = awaiting.artifact,
            )
        viewModelScope.launch {
            launchInstaller(awaiting.manifest, awaiting.mandatory, awaiting.artifact)
        }
    }

    private suspend fun launchInstaller(
        manifest: UpdateManifest,
        mandatory: Boolean,
        artifact: ani.saikou.domain.model.update.UpdateArtifactRef,
    ) {
        try {
            installUpdate(artifact)
            // Intent fired; the system installer takes over. Leave the dialog
            // visible behind — if the user cancels system-side they're back where
            // they were.
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: UpdateError) {
            // Drop the artifact on any install-time failure. The most common
            // cause is an evicted APK (Android cleared cacheDir under storage
            // pressure or the user manually cleared app cache) — preserving
            // the artifact would trap Retry in a loop launching a dead path.
            // Re-downloading on Retry is the robust default; bandwidth cost
            // is bounded (one APK).
            _uiState.value = UpdateUiState.Failed(manifest, mandatory, e, artifact = null)
        } catch (e: Throwable) {
            logger.reportError(LOG_AREA, "launchInstaller", e)
            _uiState.value =
                UpdateUiState.Failed(
                    manifest = manifest,
                    mandatory = mandatory,
                    error = UpdateError.InstallerLaunchFailed(e),
                    artifact = null,
                )
        }
    }

    private suspend fun beginDownload(
        manifest: UpdateManifest,
        mandatory: Boolean,
    ) {
        _uiState.value = UpdateUiState.Downloading(manifest, mandatory, bytesRead = 0L, totalBytes = null)
        downloadUpdate(manifest).collect { progress ->
            when (progress) {
                UpdateProgress.Idle -> Unit
                is UpdateProgress.Downloading ->
                    _uiState.update {
                        UpdateUiState.Downloading(manifest, mandatory, progress.bytesRead, progress.totalBytes)
                    }
                UpdateProgress.Verifying ->
                    _uiState.value = UpdateUiState.Verifying(manifest, mandatory)
                is UpdateProgress.Ready ->
                    _uiState.value = UpdateUiState.ReadyToInstall(manifest, mandatory, progress.artifact)
                is UpdateProgress.Failed -> {
                    logger.reportWarning(
                        area = LOG_AREA,
                        method = "beginDownload",
                        message = "download failed: ${progress.error::class.simpleName}",
                    )
                    _uiState.value = UpdateUiState.Failed(manifest, mandatory, progress.error)
                }
            }
        }
    }

    private companion object {
        private const val LOG_AREA = "self-update"
    }
}
