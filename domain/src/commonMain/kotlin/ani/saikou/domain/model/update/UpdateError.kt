package ani.saikou.domain.model.update

/**
 * Domain-facing failure modes for the self-update flow. Modelled as a
 * [`Throwable`] hierarchy so use cases can both throw and wrap in
 * `Result<>` without losing structured error context.
 *
 * Cancellation is intentionally NOT represented here — coroutine
 * cancellation is signalled by [`kotlin.coroutines.cancellation.CancellationException`]
 * and must continue to propagate unchanged.
 */
sealed class UpdateError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class NetworkUnavailable(
        cause: Throwable? = null,
    ) : UpdateError("Network unavailable", cause)

    class ManifestUnreachable(
        val statusCode: Int? = null,
        cause: Throwable? = null,
    ) : UpdateError("Manifest unreachable (status=${statusCode ?: "unknown"})", cause)

    class ManifestMalformed(
        cause: Throwable? = null,
    ) : UpdateError("Manifest malformed", cause)

    class DownloadFailed(
        cause: Throwable? = null,
    ) : UpdateError("Update download failed", cause)

    data object ChecksumMismatch : UpdateError("APK checksum mismatch") {
        private fun readResolve(): Any = ChecksumMismatch
    }

    data object PackageMismatch : UpdateError("APK package name mismatch") {
        private fun readResolve(): Any = PackageMismatch
    }

    data object VersionMismatch : UpdateError("APK versionCode mismatch") {
        private fun readResolve(): Any = VersionMismatch
    }

    data object SignerMismatch : UpdateError("APK signing certificate mismatch") {
        private fun readResolve(): Any = SignerMismatch
    }

    class InstallerLaunchFailed(
        cause: Throwable? = null,
    ) : UpdateError("Failed to launch system installer", cause)
}
