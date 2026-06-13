package ani.saikou.domain.repository

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.anime.AnimeSourceResult

/**
 * Aggregator over the configured anime stream sources. Today there's only
 * one provider (GogoAnime), but the interface lets future sources drop in
 * without the VM/use cases noticing — same shape as
 * [MangaSourceRepository] for the manga side.
 *
 * Returns [`AnimeSourceResult`] rather than bare collections so the UI can
 * tell "source actively blocked us" from "source returned no matches" — the
 * previous contract collapsed every failure into an empty list and the
 * player mislabelled Cloudflare outages as "anime not found".
 */
interface AnimeSourceRepository {
    /** Search the source catalog for the given title. */
    suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>>

    /** All episodes the source hosts for the given catalog slug. */
    suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>>

    /**
     * Resolved playable streams for the given episode embed URL. Multiple
     * variants may be returned (different quality / mirror); caller picks.
     */
    suspend fun getStreamLinks(episodeUrl: String): AnimeSourceResult<List<StreamLink>>
}
