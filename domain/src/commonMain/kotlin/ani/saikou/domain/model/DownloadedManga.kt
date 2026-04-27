package ani.saikou.domain.model

/**
 * Per-series metadata for a manga that has at least one downloaded chapter.
 * Storage-agnostic projection of the Room `downloaded_manga` row.
 */
data class DownloadedManga(
    val mangaId: Int,
    val title: String,
    val coverUrl: String?,
    val sourceId: String,
)
