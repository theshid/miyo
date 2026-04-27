package ani.saikou.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // "{mangaId}_{chapterKey}"
    val mangaId: Int,
    val mangaTitle: String,
    val chapterKey: String,    // Source-specific id (MangaDex UUID, MangaPill slug, …)
    val chapterNumber: Int,    // Universal lookup key; -1 for legacy rows pre-v6 schema
    val chapterName: String,
    val sourceId: String,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
    val totalPages: Int,
    val downloadedPages: Int,
    val fileSizeBytes: Long = 0,  // Measured at completion; 0 until then
    val createdAt: Long = System.currentTimeMillis(),
    /** How many times processQueue has auto-retried this row after an ERROR. Capped to keep
     *  genuinely-unfetchable chapters from cycling forever. Reset by a manual re-queue. */
    val attemptCount: Int = 0,
)

@Entity(tableName = "downloaded_manga")
data class DownloadedMangaEntity(
    @PrimaryKey
    val mangaId: Int,
    val title: String,
    val coverUrl: String?,
    val sourceId: String,
)
