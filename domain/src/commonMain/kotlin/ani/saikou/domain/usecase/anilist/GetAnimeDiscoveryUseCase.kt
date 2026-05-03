package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.AnimeDiscoverySnapshot
import ani.saikou.domain.repository.AnilistRepository
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * "Load the anime discovery screen" — fans out the three parallel reads
 * (trending / recently-updated / popular page 1), correlates the result
 * with the repo's transport-failure signal, and emits a telemetry warning
 * when all three are empty without a transport failure (AniList should
 * always have content for these queries; an unexplained empty is worth
 * surfacing).
 */
class GetAnimeDiscoveryUseCase(
    private val repository: AnilistRepository,
    private val logger: Logger,
) {
    suspend operator fun invoke(): AnimeDiscoverySnapshot =
        coroutineScope {
            val trendingDeferred = async { repository.getTrendingAnime() }
            val updatedDeferred = async { repository.getRecentlyUpdatedAnime() }
            val popularDeferred = async { repository.getPopularAnime(page = 1) }

            val trending = trendingDeferred.await()
            val updated = updatedDeferred.await()
            val popular = popularDeferred.await()

            val networkFailure = repository.lastFailure.value
            val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()
            val failure = if (allEmpty) networkFailure else null

            if (allEmpty && networkFailure == null) {
                logger.reportWarning(
                    area = AREA,
                    method = METHOD,
                    message = "Anime tab rendered empty (all 3 AniList sections returned 0 results)",
                )
            }

            AnimeDiscoverySnapshot(
                trending = trending,
                recentlyUpdated = updated,
                popular = popular,
                failure = failure,
            )
        }

    companion object {
        private const val AREA = "AnimeTab"
        private const val METHOD = "GetAnimeDiscoveryUseCase"
    }
}
