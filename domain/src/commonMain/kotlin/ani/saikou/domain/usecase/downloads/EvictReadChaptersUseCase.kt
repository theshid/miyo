package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.EvictionSummary
import ani.saikou.domain.repository.DownloadRepository

/**
 * "Free up space" — deletes every completed-and-read chapter (≥80%
 * read) plus its on-disk pages. Returns a tally of what was removed
 * so the screen can surface a "freed N MB" snackbar.
 */
class EvictReadChaptersUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(): EvictionSummary = repository.evictReadChapters()
}
