package ani.saikou.data.remote.news

import ani.saikou.domain.model.AiringScheduleItem
import ani.saikou.domain.model.NewsCategory
import ani.saikou.domain.model.NewsItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JikanNewsSource : NewsSource {
    override val name = "MAL"
    private val baseUrl = "https://api.jikan.moe/v4"
    private val client = HttpClient(OkHttp)
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun getLatestNews(page: Int): List<NewsItem> =
        withContext(Dispatchers.IO) {
            // Get currently airing anime news
            try {
                val response =
                    client.get("$baseUrl/seasons/now?page=$page&limit=10") {
                        header("User-Agent", "Saikou/2.0")
                    }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val data = root["data"]?.jsonArray ?: return@withContext emptyList()

                data.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val imageUrl =
                            obj["images"]
                                ?.jsonObject
                                ?.get("jpg")
                                ?.jsonObject
                                ?.get("large_image_url")
                                ?.jsonPrimitive
                                ?.content
                        val url = obj["url"]?.jsonPrimitive?.content ?: ""
                        val episodes = obj["episodes"]?.jsonPrimitive?.content
                        val status = obj["status"]?.jsonPrimitive?.content ?: ""

                        NewsItem(
                            title = "$title — ${status.ifEmpty { "Airing" }}",
                            description = "Episodes: ${episodes ?: "?"} • ${obj["synopsis"]?.jsonPrimitive?.content?.take(120) ?: ""}",
                            url = url,
                            imageUrl = imageUrl,
                            source = name,
                            date = System.currentTimeMillis(),
                            category = NewsCategory.SCHEDULE,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    override suspend fun getNewsForMedia(
        title: String,
        malId: Int?,
    ): List<NewsItem> =
        withContext(Dispatchers.IO) {
            if (malId == null) return@withContext emptyList()
            try {
                delay(350) // Rate limit: 3 req/sec
                val response =
                    client.get("$baseUrl/anime/$malId/news") {
                        header("User-Agent", "Saikou/2.0")
                    }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val data = root["data"]?.jsonArray ?: return@withContext emptyList()

                data.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        NewsItem(
                            title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            description = obj["excerpt"]?.jsonPrimitive?.content ?: "",
                            url = obj["url"]?.jsonPrimitive?.content ?: "",
                            imageUrl =
                                obj["images"]
                                    ?.jsonObject
                                    ?.get("jpg")
                                    ?.jsonObject
                                    ?.get("image_url")
                                    ?.jsonPrimitive
                                    ?.content,
                            source = name,
                            date = System.currentTimeMillis(),
                            category = NewsCategory.INDUSTRY_NEWS,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getSchedule(day: String): List<AiringScheduleItem> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get("$baseUrl/schedules?filter=$day&limit=20") {
                        header("User-Agent", "Saikou/2.0")
                    }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val data = root["data"]?.jsonArray ?: return@withContext emptyList()

                data.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        AiringScheduleItem(
                            mediaId = obj["mal_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            imageUrl =
                                obj["images"]
                                    ?.jsonObject
                                    ?.get("jpg")
                                    ?.jsonObject
                                    ?.get("large_image_url")
                                    ?.jsonPrimitive
                                    ?.content,
                            airingTime =
                                obj["broadcast"]
                                    ?.jsonObject
                                    ?.get("time")
                                    ?.jsonPrimitive
                                    ?.content ?: "",
                            episode = obj["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
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
