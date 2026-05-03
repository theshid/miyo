package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/** Pagination read for the "Popular" rail on the manga discovery screen. */
class LoadMorePopularMangaUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(page: Int): List<Media> = repository.getPopularManga(page)
}
