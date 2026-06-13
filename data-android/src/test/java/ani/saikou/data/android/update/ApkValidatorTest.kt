package ani.saikou.data.android.update

import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkValidatorTest {
    private val validator = ApkValidator(expectedPackage = "ani.saikou.v2")

    private val installedSigner = "abc123" + "0".repeat(58)
    private val manifest =
        UpdateManifest(
            versionCode = 5,
            versionName = "1.3.0",
            minimumSupportedVersion = 1,
            apkUrl = "https://example.invalid/miyo.apk",
            sha256 = "0".repeat(64),
            releaseNotes = "",
        )

    @Test
    fun `valid apk passes all checks`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 5,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, installedSigner)

        assertEquals(ApkValidator.Result.Ok, result)
    }

    @Test
    fun `package name mismatch is reported as PackageMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = "com.evil.repackage",
                versionCode = 5,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.PackageMismatch, result.error)
    }

    @Test
    fun `null package name is reported as PackageMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = null,
                versionCode = 5,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.PackageMismatch, result.error)
    }

    @Test
    fun `versionCode mismatch is reported as VersionMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 99,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.VersionMismatch, result.error)
    }

    @Test
    fun `null versionCode is reported as VersionMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = null,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.VersionMismatch, result.error)
    }

    @Test
    fun `signer mismatch is reported as SignerMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 5,
                signerSha256 = "different" + "0".repeat(55),
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.SignerMismatch, result.error)
    }

    @Test
    fun `null observed signer is reported as SignerMismatch`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 5,
                signerSha256 = null,
            )

        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.SignerMismatch, result.error)
    }

    @Test
    fun `null installed signer means we refuse — refuse to install without verification`() {
        // Defensive: even if the APK self-reports a signer, missing reference signer means we
        // cannot prove a match. SignerMismatch is the safe verdict.
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 5,
                signerSha256 = installedSigner,
            )

        val result = validator.validate(observed, manifest, expectedSigner = null) as ApkValidator.Result.Failed
        assertEquals(UpdateError.SignerMismatch, result.error)
    }

    @Test
    fun `signer comparison is case-insensitive`() {
        val observed =
            ObservedApkInfo(
                packageName = "ani.saikou.v2",
                versionCode = 5,
                signerSha256 = installedSigner.uppercase(),
            )

        val result = validator.validate(observed, manifest, installedSigner.lowercase())
        assertEquals(ApkValidator.Result.Ok, result)
    }

    @Test
    fun `package mismatch wins over version + signer mismatches — short-circuit on first failure`() {
        val observed =
            ObservedApkInfo(
                packageName = "com.other",
                versionCode = 99,
                signerSha256 = null,
            )
        val result = validator.validate(observed, manifest, installedSigner) as ApkValidator.Result.Failed
        assertEquals(UpdateError.PackageMismatch, result.error)
        assertTrue(result.error is UpdateError.PackageMismatch)
    }
}
