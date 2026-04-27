package ani.saikou.domain.model

/**
 * Domain view of a single downloadable chapter row. Mirrors the shape of
 * the Room `DownloadEntity` but without storage annotations — repos are
 * responsible for adapting in/out at the boundary so VMs and use cases
 * stay storage-agnostic.
 */
data class Download(
    /** Stable id, format `"{mangaId}_{chapterNumber}"`. */
    val id: String,
    val mangaId: Int,
    val mangaTitle: String,
    /** Source-specific chapter identifier (MangaDex UUID, MangaPill URL path). */
    val chapterKey: String,
    /** Universal lookup key — always non-negative for valid downloads. */
    val chapterNumber: Int,
    val chapterName: String,
    val sourceId: String,
    val status: DownloadStatus,
    val totalPages: Int,
    val downloadedPages: Int,
    val fileSizeBytes: Long,
    val createdAt: Long,
)
