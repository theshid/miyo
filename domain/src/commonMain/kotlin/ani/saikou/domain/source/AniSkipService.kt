package ani.saikou.domain.source

import ani.saikou.domain.model.SkipTimes

/**
 * Outbound contract for the AniSkip community API. Today the only impl
 * is HTTP-backed; the interface lets a future on-device cache (or a
 * different provider) drop in without the repository or VM noticing.
 */
interface AniSkipService {
    /**
     * Best-effort fetch of OP/ED skip intervals. Failures are swallowed
     * inside the impl and surface as [SkipTimes.EMPTY] — players prefer
     * "no skip controls" over a thrown exception.
     */
    suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
    ): SkipTimes
}
