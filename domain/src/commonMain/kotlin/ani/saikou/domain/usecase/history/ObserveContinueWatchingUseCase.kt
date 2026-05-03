package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.WatchHistoryItem
import ani.saikou.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveContinueWatchingUseCase(
    private val repository: HistoryRepository,
) {
    operator fun invoke(limit: Int = 10): Flow<List<WatchHistoryItem>> = repository.observeContinueWatching(limit)
}
