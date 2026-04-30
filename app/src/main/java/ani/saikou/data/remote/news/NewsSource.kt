package ani.saikou.data.remote.news

import ani.saikou.domain.model.NewsItem

interface NewsSource {
    val name: String

    suspend fun getLatestNews(page: Int = 1): List<NewsItem>

    suspend fun getNewsForMedia(
        title: String,
        malId: Int? = null,
    ): List<NewsItem>
}
