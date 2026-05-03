package ani.saikou.domain.usecase.history

import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.repository.HistoryRepository

class GetReadingHistoryForMediaUseCase(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(mangaId: Int): ReadingHistoryItem? = repository.getReadingHistoryFor(mangaId)
}
