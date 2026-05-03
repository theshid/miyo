package ani.saikou.data.remote.news

import ani.saikou.domain.model.NewsCategory
import ani.saikou.domain.model.NewsItem
import ani.saikou.domain.source.NewsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class ANNNewsSource : NewsSource {
    override val name = "ANN"

    override suspend fun getLatestNews(page: Int): List<NewsItem> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    Jsoup
                        .connect("https://www.animenewsnetwork.com/newsfeed/")
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(15000)
                        .get()

                // ANN news feed is RSS-like HTML. Parse article entries
                doc
                    .select("div.herald, div.wrap, div.mainfeed-section div.wrap a.herald")
                    .mapNotNull { item ->
                        try {
                            val link = item.select("a[href]").first()
                            val title =
                                item.select("h3, .herald-title, span").first()?.text()
                                    ?: link?.text() ?: return@mapNotNull null
                            val href = link?.attr("abs:href") ?: return@mapNotNull null
                            val img = item.select("img").attr("abs:src").ifEmpty { null }
                            val dateText = item.select("time, .byline, .herald-dateline").text()

                            NewsItem(
                                title = title,
                                description = dateText,
                                url = href,
                                imageUrl = img,
                                source = name,
                                date = System.currentTimeMillis(),
                                category = NewsCategory.INDUSTRY_NEWS,
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.take(20)
            } catch (e: Exception) {
                emptyList()
            }
        }

    override suspend fun getNewsForMedia(
        title: String,
        malId: Int?,
    ): List<NewsItem> {
        // ANN doesn't have a per-media API, return empty
        return emptyList()
    }
}
