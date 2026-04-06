package ani.saikou.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // "{mangaId}_{chapterKey}"
    val mangaId: Int,
    val mangaTitle: String,
    val chapterKey: String,
    val chapterName: String,
    val sourceId: String,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
    val totalPages: Int,
    val downloadedPages: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloaded_manga")
data class DownloadedMangaEntity(
    @PrimaryKey
    val mangaId: Int,
    val title: String,
    val coverUrl: String?,
    val sourceId: String,
)
