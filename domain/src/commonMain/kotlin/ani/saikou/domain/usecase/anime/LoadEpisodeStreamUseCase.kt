package ani.saikou.domain.usecase.anime

import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.repository.AnimeSourceRepository

/**
 * "Resolve playable streams for episode N of source [slug]." — composes
 * the two-step source pipeline (episode list → embed URL → direct stream
 * variants). Returns null when the episode isn't on the source's list;
 * the player surfaces that as a "not found" error.
 */
class LoadEpisodeStreamUseCase(
    private val repository: AnimeSourceRepository,
) {
    suspend operator fun invoke(
        slug: String,
        episodeNumber: Int,
    ): List<StreamLink>? {
        val episodes = repository.getEpisodes(slug)
        val episodeUrl =
            episodes
                .find { it.number == episodeNumber.toString() }
                ?.link
                ?: return null
        return repository.getStreamLinks(episodeUrl)
    }
}
