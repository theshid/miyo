package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Show my manga in this status bucket" — returns the user's manga list
 * filtered to a single AniList MediaListStatus.
 */
class GetUserMangaListUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(status: String): List<Media> = repository.getUserMangaList(status)
}
