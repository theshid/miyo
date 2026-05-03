package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Anime airing in this season" — paginated. `season` is the AniList
 * uppercase token ("WINTER", "SPRING", "SUMMER", "FALL").
 */
class GetSeasonalAnimeUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(
        season: String,
        year: Int,
        page: Int = 1,
    ): List<Media> = repository.getSeasonalAnime(season, year, page)
}
