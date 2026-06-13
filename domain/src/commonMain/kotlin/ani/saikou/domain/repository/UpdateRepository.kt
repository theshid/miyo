package ani.saikou.domain.repository

import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateArtifactRef
import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.model.update.UpdateProgress
import kotlinx.coroutines.flow.Flow

/**
 * Single seam between the platform-agnostic update orchestration (use cases,
 * VM) and the Android-specific side (HTTP transport, file I/O, PackageManager,
 * intent surface). Every method throws [`ani.saikou.domain.model.update.UpdateError`]
 * for known failure modes — except [downloadAndPrepare], which emits failures
 * through the progress flow so the VM can stay subscribed.
 */
interface UpdateRepository {
    /** GET the remote manifest and decode it. Throws on transport / malformed JSON. */
    suspend fun fetchManifest(): UpdateManifest

    /** Snapshot of this app's installed version + signing certificate fingerprint. */
    suspend fun installedAppInfo(): InstalledAppInfo

    /**
     * Stream the APK into the app's update cache directory, then verify
     * checksum + package name + versionCode + signing certificate before
     * emitting [`UpdateProgress.Ready`]. Cancellation aborts the download
     * and removes the partial file.
     */
    fun downloadAndPrepare(manifest: UpdateManifest): Flow<UpdateProgress>

    /** True if the user has granted REQUEST_INSTALL_PACKAGES (Android 8+). */
    fun isInstallPermissionGranted(): Boolean

    /**
     * Fire ACTION_MANAGE_UNKNOWN_APP_SOURCES for this package so the user
     * can flip the toggle, then return to the app.
     */
    fun openInstallPermissionSettings()

    /** Hand the artifact off to the system installer (ACTION_VIEW). */
    suspend fun startInstall(artifact: UpdateArtifactRef)

    /**
     * Delete stale APKs from the update cache. If [`current`] is non-null,
     * the file matching its versionCode is preserved; everything else goes.
     */
    suspend fun cleanupStaleArtifacts(current: UpdateManifest? = null)
}
