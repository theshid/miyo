package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Show my anime in this status bucket" — returns the user's anime list
 * filtered to a single AniList MediaListStatus (CURRENT/COMPLETED/PAUSED/
 * PLANNING/DROPPED). The status string is AniList's own, not a UI label.
 */
class GetUserAnimeListUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(status: String): List<Media> = repository.getUserAnimeList(status)
}
