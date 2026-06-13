package ani.saikou.data.android.update

import ani.saikou.domain.model.update.UpdateManifest
import kotlinx.serialization.Serializable

/**
 * Wire-format mirror of [`UpdateManifest`]. Carries the kotlinx.serialization
 * annotation so :domain can stay plain. Unknown JSON fields are dropped at
 * the Json configuration level — see [`UpdateRepositoryImpl`].
 */
@Serializable
internal data class UpdateManifestDto(
    val versionCode: Int,
    val versionName: String,
    val minimumSupportedVersion: Int,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val mandatory: Boolean = false,
    val publishedAt: String? = null,
) {
    fun toDomain(): UpdateManifest =
        UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            minimumSupportedVersion = minimumSupportedVersion,
            apkUrl = apkUrl,
            sha256 = sha256.lowercase(),
            releaseNotes = releaseNotes,
            mandatory = mandatory,
            publishedAt = publishedAt,
        )
}
