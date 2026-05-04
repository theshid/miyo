package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.repository.HistoryRepository

class UpsertReadingHistoryUseCase(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(item: ReadingHistoryItem) {
        repository.upsertReadingHistory(item)
    }
}
