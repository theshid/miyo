package ani.saikou.data.repository

import ani.saikou.domain.model.SkipTimes
import ani.saikou.domain.repository.AnimeSkipRepository
import ani.saikou.domain.source.AniSkipService

class AnimeSkipRepositoryImpl(
    private val service: AniSkipService,
) : AnimeSkipRepository {
    override suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
    ): SkipTimes = service.getSkipTimes(malId, episodeNumber)
}
