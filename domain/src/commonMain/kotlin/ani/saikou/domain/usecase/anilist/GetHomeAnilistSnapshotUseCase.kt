package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.HomeAnilistSnapshot
import ani.saikou.domain.repository.AnilistRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock

/**
 * "Open the home screen" — initial-load fan-out: user / continue-watching
 * (CURRENT + REPEATING) / continue-reading (CURRENT + REPEATING) /
 * recommendations. Derives the airing schedule from the watching list
 * (entries with a future airing time, soonest-first) and reports a
 * transport failure only when *every* read came back empty.
 */
class GetHomeAnilistSnapshotUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(): HomeAnilistSnapshot =
        coroutineScope {
            val userDeferred = async { repository.getUserData() }
            val watchingDeferred =
                async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred =
                async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }
            val recommendationsDeferred = async { repository.getRecommendations() }

            val watching = watchingDeferred.await()
            val reading = readingDeferred.await()
            val recommendations = recommendationsDeferred.await()
            val user = userDeferred.await()

            val now = Clock.System.now().toEpochMilliseconds()
            val airing =
                watching
                    .filter { entry -> entry.nextAiringEpisodeTime?.let { it > now } == true }
                    .sortedBy { it.nextAiringEpisodeTime }

            val networkFailure = repository.lastFailure.value
            val nothingLoaded =
                user == null && watching.isEmpty() && reading.isEmpty() && recommendations.isEmpty()

            HomeAnilistSnapshot(
                user = user,
                continueWatching = watching,
                continueReading = reading,
                recommendations = recommendations,
                airingSchedule = airing,
                failure = if (nothingLoaded) networkFailure else null,
            )
        }
}
