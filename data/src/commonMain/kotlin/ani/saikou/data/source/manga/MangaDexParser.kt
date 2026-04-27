package ani.saikou.data.source.manga

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.source.MangaSource
import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
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

/**
 * MangaDex implementation of [MangaSource]. Lives in commonMain — the body
 * uses only Ktor + kotlinx.serialization, both KMP. The HTTP engine is
 * supplied via the injected [HttpClient] so platform-specific engine choice
 * (OkHttp on Android, Darwin on iOS) stays in the DI graph.
 */
class MangaDexParser(
    private val client: HttpClient,
    private val logger: Logger,
) : MangaSource {

    companion object {
        private const val API = "https://api.mangadex.org"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun search(query: String): List<MangaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$API/manga") {
                parameter("limit", "25")
                parameter("title", query)
                parameter("order[relevance]", "desc")
                parameter("includes[]", "cover_art")
                header("User-Agent", "Miyo/2.0")
            }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val data = root["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { item ->
                try {
                    val obj = item.jsonObject
                    val attrs = obj["attributes"]?.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val titleObj = attrs?.get("title")?.jsonObject
                    val title = titleObj?.get("en")?.jsonPrimitive?.content
                        ?: titleObj?.values?.firstOrNull()?.jsonPrimitive?.content
                        ?: "Unknown"
                    val coverFileName = Regex("""(?<="fileName":").+?(?=")""")
                        .find(obj["relationships"]?.jsonArray?.toString() ?: "")?.value
                    val coverUrl = if (coverFileName != null)
                        "https://uploads.mangadex.org/covers/$id/$coverFileName.256.jpg" else null

                    // `lastChapter` is the final chapter the series is known
                    // to have. For licensed titles MangaDex still reports it
                    // (e.g. "220") while actually hosting only a few chapters
                    // — the mismatch lets us detect partial catalogs upstream.
                    val totalChapterHint = attrs?.get("lastChapter")?.let {
                        if (it is JsonNull) null else it.jsonPrimitive.content.toFloatOrNull()?.toInt()
                    }

                    MangaSearchResult(
                        id = id,
                        title = title,
                        coverUrl = coverUrl,
                        totalChapterHint = totalChapterHint,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            reportParserIssue("search", e, mapOf("query" to query))
            emptyList()
        }
    }

    override suspend fun getChapters(sourceId: String): List<Chapter> = withContext(Dispatchers.IO) {
        val mangaId = sourceId
        try {
            // Try English first, then fall back to any language
            var response = client.get("$API/manga/$mangaId/feed") {
                parameter("limit", "500")
                parameter("order[chapter]", "asc")
                parameter("translatedLanguage[]", "en")
                parameter("includes[]", "scanlation_group")
                header("User-Agent", "Miyo/2.0")
            }
            var root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            var data = root["data"]?.jsonArray

            // Fallback: no English chapters — try without language filter
            if (data == null || data.isEmpty()) {
                response = client.get("$API/manga/$mangaId/feed") {
                    parameter("limit", "500")
                    parameter("order[chapter]", "asc")
                    parameter("includes[]", "scanlation_group")
                    header("User-Agent", "Miyo/2.0")
                }
                root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                data = root["data"]?.jsonArray
            }

            if (data == null || data.isEmpty()) return@withContext emptyList()

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
            reportParserIssue("getChapters", e, mapOf("mangaId" to mangaId))
            emptyList()
        }
    }

    override suspend fun getPages(chapterId: String): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$API/at-home/server/$chapterId") {
                header("User-Agent", "Miyo/2.0")
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
            reportParserIssue("getPages", e, mapOf("chapterId" to chapterId))
            emptyList()
        }
    }

    private fun reportParserIssue(method: String, throwable: Throwable, extras: Map<String, String> = emptyMap()) {
        logger.reportError(area = "MangaDexParser", method = method, throwable = throwable, extras = extras)
    }
}
