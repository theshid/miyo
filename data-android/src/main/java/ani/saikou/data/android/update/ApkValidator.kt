package ani.saikou.data.android.update

import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest

/**
 * Read-only observation of an APK on disk — what the PackageManager reported
 * about its package name, versionCode, and signing certificate fingerprint.
 * Lifted to a data class so [`ApkValidator`] is testable without an Android
 * runtime.
 */
internal data class ObservedApkInfo(
    val packageName: String?,
    val versionCode: Int?,
    val signerSha256: String?,
)

/**
 * Pure validation: the downloaded APK must match the application id we ship
 * under, the versionCode the manifest advertises, AND the signing certificate
 * of the currently installed app. The package check is the cheap gate; the
 * signer check is the security one (Android would also reject a mismatched
 * signer at install time, but failing fast here gives a clear error message).
 */
internal class ApkValidator(
    private val expectedPackage: String,
) {
    sealed interface Result {
        data object Ok : Result

        data class Failed(
            val error: UpdateError,
        ) : Result
    }

    fun validate(
        observed: ObservedApkInfo,
        manifest: UpdateManifest,
        expectedSigner: String?,
    ): Result {
        if (observed.packageName != expectedPackage) {
            return Result.Failed(UpdateError.PackageMismatch)
        }
        if (observed.versionCode == null || observed.versionCode != manifest.versionCode) {
            return Result.Failed(UpdateError.VersionMismatch)
        }
        val actual = observed.signerSha256?.lowercase()
        val expected = expectedSigner?.lowercase()
        if (expected.isNullOrBlank() || actual.isNullOrBlank() || actual != expected) {
            return Result.Failed(UpdateError.SignerMismatch)
        }
        return Result.Ok
    }
}
