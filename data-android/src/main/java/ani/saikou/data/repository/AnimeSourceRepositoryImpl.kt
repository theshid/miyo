package ani.saikou.data.repository

import ani.saikou.data.source.anime.GogoParser
import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.repository.AnimeSourceRepository

/**
 * Single-provider impl that forwards to GogoParser. Lives in :data-android
 * because GogoParser is JVM-only (Jsoup) and Android-bound (`android.net.Uri`).
 */
class AnimeSourceRepositoryImpl(
    private val gogo: GogoParser,
) : AnimeSourceRepository {
    override suspend fun search(query: String): List<AnimeSearchResult> = gogo.search(query)
}
