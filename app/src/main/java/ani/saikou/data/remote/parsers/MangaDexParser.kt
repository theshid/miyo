package ani.saikou.data.remote.parsers

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MangaDexParser {

    companion object {
        private const val API = "https://api.mangadex.org"
    }

    private val client = HttpClient(OkHttp)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String): List<MangaSource> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$API/manga") {
                parameter("limit", "25")
                parameter("title", query)
                parameter("order[relevance]", "desc")
                parameter("includes[]", "cover_art")
                header("User-Agent", "Saikou/2.0")
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { item ->
                try {
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val titleObj = obj["attributes"]?.jsonObject?.get("title")?.jsonObject
                    val title = titleObj?.get("en")?.jsonPrimitive?.content
                        ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.content
                        ?: "Unknown"
                    val coverFileName = Regex("""(?<="fileName":").+?(?=")""")
                        .find(obj["relationships"]?.jsonArray?.toString() ?: "")?.value
                    val coverUrl = if (coverFileName != null)
                        "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg" else null

                    MangaSource(id = id, title = title, coverUrl = coverUrl)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getChapters(mangaId: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$API/manga/$mangaId/feed") {
                parameter("limit", "500")
                parameter("order[chapter]", "asc")
                parameter("translatedLanguage[]", "en")
                parameter("includes[]", "scanlation_group")
                header("User-Agent", "Saikou/2.0")
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { item ->
                try {
                    val attrs = item.jsonObject["attributes"]?.jsonObject ?: return@mapNotNull null

                    // Skip externally-hosted chapters
                    val externalUrl = attrs["externalUrl"]
                    if (externalUrl != null && externalUrl !is JsonNull) return@mapNotNull null

                    val chapterNum = attrs["chapter"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = attrs["title"]?.let {
                        if (it is JsonNull) "" else it.jsonPrimitive.content
                    } ?: ""
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val number = chapterNum.toFloatOrNull() ?: return@mapNotNull null

                    Chapter(
                        id = id,
                        number = number,
                        name = if (title.isNotEmpty()) "Ch. $chapterNum - $title" else "Ch. $chapterNum",
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPages(chapterId: String): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$API/at-home/server/$chapterId") {
                header("User-Agent", "Saikou/2.0")
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val baseUrl = root["baseUrl"]?.jsonPrimitive?.content ?: return@withContext emptyList()
            val chapterObj = root["chapter"]?.jsonObject ?: return@withContext emptyList()
            val hash = chapterObj["hash"]?.jsonPrimitive?.content ?: return@withContext emptyList()

            // Prefer full quality, fall back to data-saver
            val fullData = chapterObj["data"]?.jsonArray
            val saverData = chapterObj["dataSaver"]?.jsonArray
            val pages = if (fullData != null && fullData.isNotEmpty()) fullData else saverData
            val quality = if (fullData != null && fullData.isNotEmpty()) "data" else "data-saver"

            pages?.mapIndexed { index, page ->
                MangaPage(
                    index = index,
                    imageUrl = "$baseUrl/$quality/$hash/${page.jsonPrimitive.content}",
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
