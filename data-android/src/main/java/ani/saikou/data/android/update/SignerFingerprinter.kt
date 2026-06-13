package ani.saikou.data.android.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/**
 * Pulls signing-certificate SHA-256 fingerprints out of an installed package
 * or an APK file on disk. The PackageManager API split at API 28 means each
 * call site needs both branches.
 *
 * For multi-signed apps we take the first apkContentsSigners entry (API 28+)
 * or the first [`Signature`] (API 26-27). Two APKs match when their first
 * signer's certificate bytes hash to the same digest.
 */
internal class SignerFingerprinter(
    private val packageManager: PackageManager,
) {
    /** SHA-256 of the named package's signing certificate, or null on failure. */
    fun installedFingerprint(packageName: String): String? =
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            info.firstSignatureSha256()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }

    /** SHA-256 of the APK on disk's signing certificate, or null on failure. */
    fun apkFingerprint(apk: File): String? =
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager.getPackageArchiveInfo(
                        apk.absolutePath,
                        PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageArchiveInfo(
                        apk.absolutePath,
                        PackageManager.GET_SIGNATURES,
                    )
                }
            info?.firstSignatureSha256()
        } catch (_: Exception) {
            null
        }

    private fun PackageInfo.firstSignatureSha256(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = signingInfo ?: return null
            val sigs: Array<Signature> =
                if (signing.hasMultipleSigners()) {
                    signing.apkContentsSigners
                } else {
                    signing.signingCertificateHistory
                }
            return sigs.firstOrNull()?.let { Sha256Streamer.hashOf(it.toByteArray()) }
        }
        @Suppress("DEPRECATION")
        val sigs = signatures ?: return null
        return sigs.firstOrNull()?.let { Sha256Streamer.hashOf(it.toByteArray()) }
    }
}
