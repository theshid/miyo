package ani.saikou.domain.model.update

/**
 * Wire-schema for the remote update manifest hosted at the configured
 * UPDATE_MANIFEST_URL. Mirrored exactly by [`UpdateManifestDto`] in :data-android,
 * which owns the kotlinx.serialization annotation. Keeping the domain model
 * plain lets us evolve the wire schema without dragging the serialization
 * plugin into :domain.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val minimumSupportedVersion: Int,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val mandatory: Boolean = false,
    val publishedAt: String? = null,
)
