package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveReadingHistoryUseCase(
    private val repository: HistoryRepository,
) {
    operator fun invoke(limit: Int = 10): Flow<List<ReadingHistoryItem>> = repository.observeRecentReading(limit)
}
