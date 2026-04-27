package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.DownloadRequest
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.repository.DownloadRepository

/**
 * Idempotent queue insertion for a single chapter. Skips the call when an
 * active row already exists (QUEUED / DOWNLOADING / COMPLETED) — those
 * states would be regressed by a fresh insert. PAUSED and ERROR rows fall
 * through and get re-queued, which is the desired retry behavior.
 *
 * Calling code uses this in two shapes:
 *   - Reader screen — single chapter from the chapter list / "next" button.
 *   - Reader screen "save next N" prompt — composed N times via
 *     [QueueNextChaptersUseCase].
 */
class QueueChapterDownloadUseCase(
    private val downloadRepo: DownloadRepository,
) {
    private val activeStatuses = setOf(
        DownloadStatus.QUEUED,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.COMPLETED,
    )

    suspend operator fun invoke(request: DownloadRequest) {
        val id = "${request.mangaId}_${request.chapterKey}"
        if (downloadRepo.getDownload(id)?.status in activeStatuses) return
        downloadRepo.queueChapter(request)
    }
}
