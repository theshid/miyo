package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.MangaDiscoverySnapshot
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * "Load the manga discovery screen" — fans out the three parallel reads
 * (trending / recently-updated / popular page 1) and correlates the
 * result with the repo's transport-failure signal.
 *
 * Unlike [GetAnimeDiscoveryUseCase] there's no empty-tab telemetry
 * warning — preserves the original screen's behavior (manga discovery
 * tolerates legitimately-empty results without phoning home).
 */
class GetMangaDiscoveryUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(): MangaDiscoverySnapshot =
        coroutineScope {
            val trendingDeferred = async { repository.getTrendingManga() }
            val updatedDeferred = async { repository.getRecentlyUpdatedManga() }
            val popularDeferred = async { repository.getPopularManga(page = 1) }

            val trending = trendingDeferred.await()
            val updated = updatedDeferred.await()
            val popular = popularDeferred.await()

            val networkFailure = repository.lastFailure.value
            val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()

            MangaDiscoverySnapshot(
                trending = trending,
                recentlyUpdated = updated,
                popular = popular,
                failure = if (allEmpty) networkFailure else null,
            )
        }
}
