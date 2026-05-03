package ani.saikou.domain.usecase.anime

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.repository.AnimeSourceRepository

/**
 * "What sources host this anime?" — used by the detail screen when the
 * user taps an episode and there's no persisted source slug. Returns
 * candidates the screen presents in a picker.
 */
class ResolveAnimeSourcesUseCase(
    private val repository: AnimeSourceRepository,
) {
    suspend operator fun invoke(query: String): List<AnimeSearchResult> = repository.search(query)
}
