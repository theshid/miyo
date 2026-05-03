package ani.saikou.domain.repository

import ani.saikou.domain.model.SkipTimes

/**
 * Domain wrapper around [ani.saikou.domain.source.AniSkipService]. The
 * single-method shape mirrors the underlying API for now — repo lives so
 * the player VM (and any future caller) goes through use cases without
 * touching the transport directly.
 */
interface AnimeSkipRepository {
    suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
    ): SkipTimes
}
