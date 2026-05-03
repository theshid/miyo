package ani.saikou.domain.repository

import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsItem

/**
 * Aggregator over the configured [ani.saikou.domain.source.NewsSource]
 * implementations. The repo owns the multi-source orchestration —
 * fan-out across MAL/Reddit/ANN, dedup, and date-sort — so VMs (via use
 * cases) just ask for "the news" without knowing about individual feeds.
 *
 * Best-effort: a transient failure in one source is swallowed; the other
 * sources still contribute their items. Returning a flat list rather
 * than per-source buckets keeps the screen filter logic simple.
 */
interface NewsRepository {
    /**
     * Fan-out across all configured sources, deduplicate by title prefix
     * (first 50 chars, case-insensitive), and sort newest-first.
     */
    suspend fun getLatestNews(): List<NewsItem>

    /**
     * Day-of-week airing schedule. `day` is the lowercase English name —
     * "monday" through "sunday". Backed by Jikan only today; the abstract
     * surface lets a future schedule provider drop in without churn.
     */
    suspend fun getSchedule(day: String): List<AiringScheduleItem>
}
