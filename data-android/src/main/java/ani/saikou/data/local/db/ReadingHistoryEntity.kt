package ani.saikou.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey
    val mangaId: Int,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapterNumber: Int,
    val chapterName: String,
    val chapterId: String, // MangaDex chapter UUID — skip search on resume
    val sourceId: String, // e.g. MangaDex manga UUID — skip search on resume
    val sourceName: String, // "MangaDex"
    val lastPage: Int, // 0-indexed page within the chapter
    val totalPages: Int,
    val lastReadAt: Long = System.currentTimeMillis(),
)
