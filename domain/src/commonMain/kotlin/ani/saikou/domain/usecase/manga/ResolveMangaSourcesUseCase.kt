package ani.saikou.domain.usecase.manga

import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.repository.MangaSourceRepository

/**
 * "What sources host this manga?" — used by the detail screen when the
 * user taps a chapter and there's no persisted source id. The repo
 * concatenates results from configured sources (MangaDex first, then
 * MangaPill); the screen presents the union in a picker.
 */
class ResolveMangaSourcesUseCase(
    private val repository: MangaSourceRepository,
) {
    suspend operator fun invoke(query: String): List<MangaSearchResult> = repository.search(query)
}
