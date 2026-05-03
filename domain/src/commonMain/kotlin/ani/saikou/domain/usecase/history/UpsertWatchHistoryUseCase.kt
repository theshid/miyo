package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.repository.HistoryRepository

/**
 * Upsert one row in the watch history table. Caller is responsible for
 * computing the right [WatchHistoryItem.completedEpisodes] (only-go-up
 * semantics) — keep that policy in the VM where the prior value is read.
 */
class UpsertWatchHistoryUseCase(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(item: WatchHistoryItem) {
        repository.upsertWatchHistory(item)
    }
}
