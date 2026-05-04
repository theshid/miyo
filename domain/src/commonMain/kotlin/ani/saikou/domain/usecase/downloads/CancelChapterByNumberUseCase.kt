package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * Cancel a download identified by chapter NUMBER rather than row id —
 * what the reader's chapter-picker has on hand. The repo resolves the
 * actual row, so this works whether the row's `chapterKey` is a real
 * source-side id (queued via `QueueNextChaptersUseCase` / lookup-based
 * single queue) or a legacy stringified chapter number.
 */
class CancelChapterByNumberUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(
        mangaId: Int,
        chapterNumber: Int,
    ) {
        repository.cancelChapterByNumber(mangaId, chapterNumber)
    }
}
