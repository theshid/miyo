package ani.saikou.data.repository

import ani.saikou.data.source.anime.GogoParser
import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.repository.AnimeSourceRepository

/**
 * Single-provider impl that forwards to GogoParser. Lives in :data-android
 * because GogoParser is JVM-only (Jsoup) and Android-bound (`android.net.Uri`).
 *
 * The forwarding is intentionally thin — the parser already returns typed
 * [`AnimeSourceResult`] outputs, and the repository interface mirrors them
 * exactly. Future providers slot in here without VM/use-case changes.
 */
class AnimeSourceRepositoryImpl(
    private val gogo: GogoParser,
) : AnimeSourceRepository {
    override suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>> = gogo.search(query)

    override suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>> = gogo.getEpisodes(slug)

    override suspend fun getStreamLinks(episodeUrl: String): AnimeSourceResult<List<StreamLink>> = gogo.getStreamLinks(episodeUrl)
}
