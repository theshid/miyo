package ani.saikou.domain.model

/**
 * One row in the user's manga reading history. One entry per manga — last
 * chapter + page within it, plus the persistent source identifiers so
 * resume can skip the source search. Mirrors the Room `reading_history`
 * table; entity stays in :data-android.
 */
data class ReadingHistoryItem(
    val mangaId: Int,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapterNumber: Int,
    val chapterName: String,
    val chapterId: String,
    val sourceId: String,
    val sourceName: String,
    val lastPage: Int,
    val totalPages: Int,
    val lastReadAt: Long,
)
