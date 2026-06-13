package ani.saikou.domain.model.anime

import ani.saikou.domain.model.StreamLink

/**
 * Output of [`ani.saikou.domain.usecase.anime.LoadEpisodeStreamUseCase`]. The
 * two-step pipeline (episode list → embed → direct stream) has two distinct
 * "nothing to play" outcomes that the player needs to render differently:
 *
 * - **EpisodeNotFound**: the source returned an episode list, but the
 *   requested number isn't in it. ("Episode 137 isn't on this source.")
 * - **Available** with an empty list: the source listed the episode but
 *   no embeds resolved to a playable URL. ("Source is bored, try another.")
 *
 * Wrapped in [`AnimeSourceResult`] so blocking / contract drift / transport
 * errors are still distinguishable from these legitimate-empty cases.
 */
sealed interface LoadedEpisodeStream {
    data class Available(
        val links: List<StreamLink>,
    ) : LoadedEpisodeStream

    data object EpisodeNotFound : LoadedEpisodeStream
}
