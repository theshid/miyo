package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.DownloadRequest

/**
 * "Save the next [count] chapters after [afterChapterNumber]" — what the
 * reader's end-of-chapter banner offers. Walks [availableChapters] (the
 * source's real chapter list, in the order the source returned them) for
 * the first [count] entries whose number is greater than the cursor, and
 * queues them via [QueueChapterDownloadUseCase] (so dedup against existing
 * QUEUED/DOWNLOADING/COMPLETED rows comes through for free).
 *
 * Each queued row carries the parser's real [Chapter.id] as `chapterKey`
 * plus the active [sourceId], so [DownloadRepository.queueChapter] doesn't
 * need to fuzzy-resolve the source at run time. This is the fix for the
 * banner's old behavior of queueing `currentChapter + 1..count` blindly,
 * which materialized phantom rows past the actual end of the manga and
 * broke the picker's chapter count.
 *
 * Returns the number of chapters actually queued (≤ [count] when fewer
 * remain). The caller decides whether to surface that to the UI.
 */
class QueueNextChaptersUseCase(
    private val queueChapter: QueueChapterDownloadUseCase,
) {
    suspend operator fun invoke(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String?,
        sourceId: String,
        availableChapters: List<Chapter>,
        afterChapterNumber: Int,
        count: Int,
    ): Int {
        if (count <= 0 || availableChapters.isEmpty()) return 0
        val upcoming =
            availableChapters
                .asSequence()
                .filter { it.number.toInt() > afterChapterNumber }
                .sortedBy { it.number }
                .take(count)
                .toList()
        for (chapter in upcoming) {
            queueChapter(
                DownloadRequest(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle,
                    coverUrl = coverUrl,
                    chapterKey = chapter.id,
                    chapterNumber = chapter.number.toInt(),
                    chapterName = chapter.name,
                    sourceId = sourceId,
                ),
            )
        }
        return upcoming.size
    }
}
