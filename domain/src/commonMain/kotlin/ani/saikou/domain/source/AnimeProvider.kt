package ani.saikou.domain.source

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.anime.AnimeSourceResult

/**
 * Single anime-streaming source (anineko, anizone, etc.) The repository
 * fans out across all configured providers — this contract is the per-source
 * shape: search the catalog, list episodes for a slug, resolve playable
 * stream URLs for an episode page.
 *
 * Each provider owns its own scraping mechanism (HTML, Livewire, JSON API,
 * etc.) — the repository doesn't care, it just maps results into
 * provider-tagged slugs the watch history can round-trip.
 */
interface AnimeProvider {
    /**
     * Stable identifier used to prefix slugs ("anineko:abc", "anizone:xyz")
     * so watch history can persist the source choice and the repository can
     * route lookups back to the right provider.
     */
    val id: String

    /**
     * Origin host (no scheme, no trailing slash, e.g. "anineko.to"). Used
     * by the repository to route stream-URL requests to the provider that
     * owns the CDN — episode URLs are stored as absolute and the host is
     * the cheapest dispatch key.
     */
    val host: String

    /** Display copy shown in the source picker. */
    val displayName: String

    suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>>

    suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>>

    suspend fun getStreamLinks(episodeUrl: String): AnimeSourceResult<List<StreamLink>>
}
