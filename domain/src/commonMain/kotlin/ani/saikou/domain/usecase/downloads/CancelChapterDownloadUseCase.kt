package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * Cancel a single chapter download — deletes the row and any partial
 * page files. Used by the downloads screen's per-chapter delete and
 * by the multi-select bulk-delete action.
 */
class CancelChapterDownloadUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(downloadId: String) {
        repository.cancelChapter(downloadId)
    }
}
