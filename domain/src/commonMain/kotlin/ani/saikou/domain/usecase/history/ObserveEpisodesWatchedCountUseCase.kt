package ani.saikou.domain.usecase.history

import ani.saikou.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveEpisodesWatchedCountUseCase(
    private val repository: HistoryRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeEpisodesWatchedCount()
}
