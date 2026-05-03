package ani.saikou.data.repository

import ani.saikou.data.remote.news.JikanNewsSource
import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsItem
import ani.saikou.domain.repository.NewsRepository
import ani.saikou.domain.source.NewsSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Aggregator over the configured [NewsSource]s. Owns the multi-source
 * orchestration the VM used to do inline: fan-out across all sources in
 * parallel via `async`, dedup by lowercase 50-char title prefix, sort
 * newest-first. Schedule fetching is Jikan-only — kept as a typed
 * dependency rather than a `NewsSource` lookup so the contract is
 * explicit.
 */
class NewsRepositoryImpl(
    private val sources: List<NewsSource>,
    private val jikan: JikanNewsSource,
) : NewsRepository {
    override suspend fun getLatestNews(): List<NewsItem> =
        coroutineScope {
            sources
                .map { async { it.getLatestNews() } }
                .awaitAll()
                .flatten()
                .distinctBy { it.title.lowercase().take(TITLE_DEDUP_PREFIX) }
                .sortedByDescending { it.date }
        }

    override suspend fun getSchedule(day: String): List<AiringScheduleItem> = jikan.getSchedule(day)

    companion object {
        private const val TITLE_DEDUP_PREFIX = 50
    }
}
