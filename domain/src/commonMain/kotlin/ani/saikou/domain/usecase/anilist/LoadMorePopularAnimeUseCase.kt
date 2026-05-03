package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * Pagination read for the "Popular this season" rail on the anime
 * discovery screen. The screen tracks its own page counter; this use
 * case is the per-page fetch.
 */
class LoadMorePopularAnimeUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(page: Int): List<Media> = repository.getPopularAnime(page)
}
