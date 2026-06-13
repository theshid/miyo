package ani.saikou.domain.usecase.anime

import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.model.anime.LoadedEpisodeStream
import ani.saikou.domain.repository.AnimeSourceRepository

/**
 * "Resolve playable streams for episode N of source [slug]." — composes
 * the two-step source pipeline (episode list → embed URL → direct stream
 * variants).
 *
 * The composite outcome is split three ways inside [`AnimeSourceResult`]:
 *
 * - `Failed(failure)` — the source rejected one of the two requests
 *   (Cloudflare challenge, transport error, contract drift). UI shows a
 *   typed message.
 * - `Success(EpisodeNotFound)` — source responded successfully but the
 *   requested episode number isn't in the list. UI shows "Episode N not
 *   found on this source".
 * - `Success(Available(links))` — episode found; [`links`] may still be
 *   empty when all embeds failed to resolve a direct stream URL.
 *
 * Failure of the EPISODES request short-circuits — there's no point
 * fetching streams when we don't know which embed to ask for.
 */
class LoadEpisodeStreamUseCase(
    private val repository: AnimeSourceRepository,
) {
    suspend operator fun invoke(
        slug: String,
        episodeNumber: Int,
    ): AnimeSourceResult<LoadedEpisodeStream> {
        val episodes =
            when (val result = repository.getEpisodes(slug)) {
                is AnimeSourceResult.Failed -> return result
                is AnimeSourceResult.Success -> result.value
            }
        val episodeUrl =
            episodes
                .find { it.number == episodeNumber.toString() }
                ?.link
                ?: return AnimeSourceResult.Success(LoadedEpisodeStream.EpisodeNotFound)
        return when (val streams = repository.getStreamLinks(episodeUrl)) {
            is AnimeSourceResult.Failed -> streams
            is AnimeSourceResult.Success -> AnimeSourceResult.Success(LoadedEpisodeStream.Available(streams.value))
        }
    }
}
