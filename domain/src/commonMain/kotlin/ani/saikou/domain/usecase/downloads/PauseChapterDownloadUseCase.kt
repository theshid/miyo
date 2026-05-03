package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * Mark a chapter download as PAUSED — the worker stops fetching pages
 * but keeps what's already on disk. A subsequent queue resumes from
 * where it left off.
 */
class PauseChapterDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(downloadId: String) {
        repository.pauseChapter(downloadId)
    }
}
