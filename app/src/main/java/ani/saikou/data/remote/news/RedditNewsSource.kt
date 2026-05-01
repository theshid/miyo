package ani.saikou.data.remote.news

import ani.saikou.domain.model.NewsCategory
import ani.saikou.domain.model.NewsItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

class RedditNewsSource : NewsSource {
    override val name = "Reddit"
    private val client = HttpClient(OkHttp)
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val ua = "android:ani.saikou.v2:v2.0 (by /u/saikou_app)"

    override suspend fun getLatestNews(page: Int): List<NewsItem> =
        withContext(Dispatchers.IO) {
            val animeNews = fetchSubreddit("anime", 15)
            val mangaNews = fetchSubreddit("manga", 10)
            (animeNews + mangaNews).sortedByDescending { it.date }
        }

    override suspend fun getNewsForMedia(
        title: String,
        malId: Int?,
    ): List<NewsItem> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(title, "UTF-8")
                val response =
                    client.get(
                        "https://www.reddit.com/r/anime/search.json?q=$encoded&restrict_sr=on&sort=new&limit=15",
                    ) {
                        header("User-Agent", ua)
                    }
                parseRedditResponse(response.bodyAsText())
            } catch (e: Exception) {
                emptyList()
            }
        }

    private suspend fun fetchSubreddit(
        sub: String,
        limit: Int,
    ): List<NewsItem> =
        try {
            val response =
                client.get("https://www.reddit.com/r/$sub/hot.json?limit=$limit") {
                    header("User-Agent", ua)
                }
            parseRedditResponse(response.bodyAsText())
        } catch (e: Exception) {
            emptyList()
        }

    private fun parseRedditResponse(body: String): List<NewsItem> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val children = root["data"]?.jsonObject?.get("children")?.jsonArray ?: return emptyList()

            children.mapNotNull { child ->
                try {
                    val data = child.jsonObject["data"]?.jsonObject ?: return@mapNotNull null
                    val title = data["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val permalink = data["permalink"]?.jsonPrimitive?.content ?: ""
                    val score = data["score"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val comments = data["num_comments"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    val created = (data["created_utc"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) * 1000
                    val flair = data["link_flair_text"]?.jsonPrimitive?.content ?: ""
                    val thumbnail = data["thumbnail"]?.jsonPrimitive?.content
                    val imageUrl = if (thumbnail != null && thumbnail.startsWith("http")) thumbnail else null
                    val sub = data["subreddit"]?.jsonPrimitive?.content ?: "anime"

                    val category =
                        when {
                            flair.contains("Episode", true) || flair.contains("Discussion", true) -> NewsCategory.DISCUSSION
                            flair.contains("News", true) -> NewsCategory.INDUSTRY_NEWS
                            flair.contains("Chapter", true) -> NewsCategory.CHAPTER_RELEASE
                            else -> NewsCategory.DISCUSSION
                        }

                    NewsItem(
                        title = title,
                        description = "r/$sub • ↑$score • $comments comments",
                        url = "https://www.reddit.com$permalink",
                        imageUrl = imageUrl,
                        source = name,
                        date = created.toLong(),
                        category = category,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
