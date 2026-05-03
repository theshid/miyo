package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.HomeAnilistSnapshot
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock

/**
 * Lightweight refresh after a list-mutation event — re-fetches user +
 * watching + reading lists (and re-derives the airing schedule), but
 * skips recommendations and the failure-correlation step. Recommendations
 * don't change often and we don't want a partial list-refresh to clear a
 * sticky failure banner.
 *
 * Returns a snapshot with the fresh data and `failure = null`,
 * `recommendations = previousRecommendations` (caller-supplied) so the
 * VM can `copy()` over the existing UI state without losing the old
 * recommendation rail.
 */
class RefreshHomeAnilistSnapshotUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(): HomeAnilistSnapshotPartial =
        coroutineScope {
            val userDeferred = async { repository.getUserData() }
            val watchingDeferred =
                async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred =
                async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }

            val watching = watchingDeferred.await()
            val reading = readingDeferred.await()
            val user = userDeferred.await()

            val now = Clock.System.now().toEpochMilliseconds()
            val airing =
                watching
                    .filter { entry -> entry.nextAiringEpisodeTime?.let { it > now } == true }
                    .sortedBy { it.nextAiringEpisodeTime }

            HomeAnilistSnapshotPartial(
                user = user,
                continueWatching = watching,
                continueReading = reading,
                airingSchedule = airing,
            )
        }
}

/**
 * Subset of [HomeAnilistSnapshot] returned by the refresh path — only
 * the fields the refresh recomputes. Recommendations + failure are
 * intentionally absent: callers preserve their existing values.
 */
data class HomeAnilistSnapshotPartial(
    val user: ani.saikou.domain.model.User?,
    val continueWatching: List<ani.saikou.domain.model.Media>,
    val continueReading: List<ani.saikou.domain.model.Media>,
    val airingSchedule: List<ani.saikou.domain.model.Media>,
)
