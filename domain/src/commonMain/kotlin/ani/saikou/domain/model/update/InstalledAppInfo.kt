package ani.saikou.domain.model.update

/**
 * Snapshot of the currently installed application, used both for version
 * comparison and for matching the downloaded APK's signing certificate.
 *
 * [signerSha256] is the lowercase hex SHA-256 of the installed app's signing
 * certificate (collapsed to a single fingerprint — for apps with multiple
 * signers we take the first apk-contents signer). `null` means the platform
 * could not surface it (very old API levels or a degraded PackageManager).
 */
data class InstalledAppInfo(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val signerSha256: String?,
)
