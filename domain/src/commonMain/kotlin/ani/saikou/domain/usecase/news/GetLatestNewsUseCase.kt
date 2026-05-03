package ani.saikou.domain.usecase.news

import ani.saikou.domain.model.NewsItem
import ani.saikou.domain.repository.NewsRepository

/**
 * "Refresh the news feed" — fans out across configured sources, dedups,
 * sorts newest-first, and returns the result. Today this is a thin
 * pass-through to [NewsRepository.getLatestNews]; future cross-cutting
 * concerns (caching, paged loading, source-weighting) land here.
 */
class GetLatestNewsUseCase(
    private val repository: NewsRepository,
) {
    suspend operator fun invoke(): List<NewsItem> = repository.getLatestNews()
}
