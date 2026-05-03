package ani.saikou.domain.usecase.anime

import ani.saikou.domain.model.SkipTimes
import ani.saikou.domain.repository.AnimeSkipRepository

/**
 * "Where are the OP/ED in this episode?" — used by the player to draw
 * the skip-intro / skip-ending overlays. Falls back to [SkipTimes.EMPTY]
 * on any failure (the player simply hides the controls).
 */
class GetEpisodeSkipTimesUseCase(
    private val repository: AnimeSkipRepository,
) {
    suspend operator fun invoke(
        malId: Int,
        episodeNumber: Int,
    ): SkipTimes = repository.getSkipTimes(malId, episodeNumber)
}
