package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Show my favorites for this media type" — returns the user's favorited
 * anime or manga (type is "ANIME" or "MANGA"). Distinct from list-status
 * tabs because favorites isn't a MediaListStatus on AniList.
 */
class GetUserFavoritesUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(type: String): List<Media> = repository.getUserFavorites(type)
}
