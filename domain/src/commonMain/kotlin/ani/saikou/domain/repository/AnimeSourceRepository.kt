package ani.saikou.domain.repository

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink

/**
 * Aggregator over the configured anime stream sources. Today there's only
 * one provider (GogoAnime), but the interface lets future sources drop in
 * without the VM/use cases noticing — same shape as
 * [MangaSourceRepository] for the manga side.
 */
interface AnimeSourceRepository {
    /** Search the source catalog for the given title; results are best-effort. */
    suspend fun search(query: String): List<AnimeSearchResult>

    /** All episodes the source hosts for the given catalog slug. */
    suspend fun getEpisodes(slug: String): List<Episode>

    /**
     * Resolved playable streams for the given episode embed URL. Multiple
     * variants may be returned (different quality / mirror); caller picks.
     */
    suspend fun getStreamLinks(episodeUrl: String): List<StreamLink>
}
