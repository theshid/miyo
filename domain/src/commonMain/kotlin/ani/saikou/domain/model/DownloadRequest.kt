package ani.saikou.domain.model

/**
 * Input shape for [ani.saikou.domain.repository.DownloadRepository.queueChapter].
 * Carries everything the queue needs to materialize a [Download] row; pages
 * may be discovered later by the resolver, hence the [totalPages] default.
 */
data class DownloadRequest(
    val mangaId: Int,
    val mangaTitle: String,
    val coverUrl: String? = null,
    val chapterKey: String,
    val chapterNumber: Int,
    val chapterName: String,
    val sourceId: String,
    /** 0 means "discover at download time"; otherwise pre-known page count. */
    val totalPages: Int = 0,
)
