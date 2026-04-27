package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.DownloadRequest

/**
 * "Save the next [count] chapters after [startChapter]" — what the
 * reader's end-of-chapter banner offers. Composes [QueueChapterDownloadUseCase]
 * so the dedup behavior comes through for free; chapters already queued
 * or completed are silently skipped.
 *
 * `sourceId` is left blank intentionally — the DownloadService re-resolves
 * the source at run time from the manga title (same path the single-chapter
 * download takes). Pre-known totalPages stays at 0 for the same reason.
 */
class QueueNextChaptersUseCase(
    private val queueChapter: QueueChapterDownloadUseCase,
) {
    suspend operator fun invoke(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String?,
        startChapter: Int,
        count: Int,
    ) {
        for (offset in 1..count) {
            val chapterNumber = startChapter + offset
            queueChapter(
                DownloadRequest(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle,
                    coverUrl = coverUrl,
                    chapterKey = chapterNumber.toString(),
                    chapterNumber = chapterNumber,
                    chapterName = "Chapter $chapterNumber",
                    sourceId = "",
                ),
            )
        }
    }
}
