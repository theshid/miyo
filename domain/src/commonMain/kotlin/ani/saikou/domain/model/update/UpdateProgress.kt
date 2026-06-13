package ani.saikou.domain.model.update

/**
 * Opaque handle for an APK that has been downloaded, checksum-verified and
 * package/version/signer-validated, and is ready to hand off to the system
 * package installer. The string contents are an implementation detail of
 * the [`UpdateRepository`] impl — VMs and screens never inspect it.
 */
@JvmInline
value class UpdateArtifactRef(
    val token: String,
)

/**
 * Streaming progress emissions from [`UpdateRepository.downloadAndPrepare`].
 * Single-shot terminal states ([`Ready`], [`Failed`]) close the flow.
 *
 * [`Downloading.totalBytes`] is null when the server omits a Content-Length
 * header (the UI then renders an indeterminate progress bar).
 */
sealed interface UpdateProgress {
    data object Idle : UpdateProgress

    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : UpdateProgress {
        /**
         * Fraction 0f..1f when [totalBytes] is known, else null.
         */
        val fraction: Float?
            get() {
                val total = totalBytes ?: return null
                if (total <= 0L) return null
                return (bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
    }

    data object Verifying : UpdateProgress

    data class Ready(
        val artifact: UpdateArtifactRef,
    ) : UpdateProgress

    data class Failed(
        val error: UpdateError,
    ) : UpdateProgress
}
