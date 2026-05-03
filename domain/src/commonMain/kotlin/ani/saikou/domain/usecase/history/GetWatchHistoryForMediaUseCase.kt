package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.repository.HistoryRepository

class GetWatchHistoryForMediaUseCase(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(mediaId: Int): WatchHistoryItem? = repository.getWatchHistoryFor(mediaId)
}
