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
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun search(query: String): List<MangaSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get(MangaDexSite.Endpoints.mangaSearch()) {
                        parameter(MangaDexSite.Params.LIMIT, MangaDexSite.Defaults.SEARCH_LIMIT)
                        parameter(MangaDexSite.Params.TITLE, query)
                        parameter(MangaDexSite.Params.ORDER_RELEVANCE, MangaDexSite.Defaults.ORDER_DESC)
                        parameter(MangaDexSite.Params.INCLUDES, MangaDexSite.Includes.COVER_ART)
                        header(MangaDexSite.Headers.USER_AGENT_KEY, MangaDexSite.USER_AGENT)
                    }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val data = root[MangaDexSite.JsonKeys.DATA]?.jsonArray ?: return@withContext emptyList()

                data.mapNotNull { item ->
                    try {
                        val obj = item.jsonObject
                        val attrs = obj[MangaDexSite.JsonKeys.ATTRIBUTES]?.jsonObject
                        val id = obj[MangaDexSite.JsonKeys.ID]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val titleObj = attrs?.get(MangaDexSite.JsonKeys.TITLE)?.jsonObject
                        val title =
                            titleObj?.get(MangaDexSite.DEFAULT_LANGUAGE)?.jsonPrimitive?.content
                                ?: titleObj
                                    ?.values
                                    ?.firstOrNull()
                                    ?.jsonPrimitive
                                    ?.content
                                ?: "Unknown"
                        val coverFileName =
                            MangaDexSite.Patterns.COVER_FILE_NAME
                                .find(obj[MangaDexSite.JsonKeys.RELATIONSHIPS]?.jsonArray?.toString() ?: "")
                                ?.value
                        val coverUrl =
                            if (coverFileName != null) {
                                MangaDexSite.Endpoints.cover(id, coverFileName)
                            } else {
                                null
                            }

                        // `lastChapter` is the final chapter the series is known
                        // to have. For licensed titles MangaDex still reports it
                        // (e.g. "220") while actually hosting only a few chapters
                        // — the mismatch lets us detect partial catalogs upstream.
                        val totalChapterHint =
                            attrs?.get(MangaDexSite.JsonKeys.LAST_CHAPTER)?.let {
                                if (it is JsonNull) {
                                    null
                                } else {
                                    it.jsonPrimitive.content
                                        .toFloatOrNull()
                                        ?.toInt()
                                }
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

    override suspend fun getChapters(sourceId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaId = sourceId
            try {
                // Try English first, then fall back to any language
                var response =
                    client.get(MangaDexSite.Endpoints.feed(mangaId)) {
                        parameter(MangaDexSite.Params.LIMIT, MangaDexSite.Defaults.FEED_LIMIT)
                        parameter(MangaDexSite.Params.ORDER_CHAPTER, MangaDexSite.Defaults.ORDER_ASC)
                        parameter(MangaDexSite.Params.TRANSLATED_LANGUAGE, MangaDexSite.DEFAULT_LANGUAGE)
                        parameter(MangaDexSite.Params.INCLUDES, MangaDexSite.Includes.SCANLATION_GROUP)
                        header(MangaDexSite.Headers.USER_AGENT_KEY, MangaDexSite.USER_AGENT)
                    }
                var root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                var data = root[MangaDexSite.JsonKeys.DATA]?.jsonArray

                // Fallback: no English chapters — try without language filter
                if (data == null || data.isEmpty()) {
                    response =
                        client.get(MangaDexSite.Endpoints.feed(mangaId)) {
                            parameter(MangaDexSite.Params.LIMIT, MangaDexSite.Defaults.FEED_LIMIT)
                            parameter(MangaDexSite.Params.ORDER_CHAPTER, MangaDexSite.Defaults.ORDER_ASC)
                            parameter(MangaDexSite.Params.INCLUDES, MangaDexSite.Includes.SCANLATION_GROUP)
                            header(MangaDexSite.Headers.USER_AGENT_KEY, MangaDexSite.USER_AGENT)
                        }
                    root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                    data = root[MangaDexSite.JsonKeys.DATA]?.jsonArray
                }

                if (data == null || data.isEmpty()) return@withContext emptyList()

                data.mapNotNull { item ->
                    try {
                        val attrs = item.jsonObject[MangaDexSite.JsonKeys.ATTRIBUTES]?.jsonObject ?: return@mapNotNull null

                        // Skip externally-hosted chapters
                        val externalUrl = attrs[MangaDexSite.JsonKeys.EXTERNAL_URL]
                        if (externalUrl != null && externalUrl !is JsonNull) return@mapNotNull null

                        val chapterNum = attrs[MangaDexSite.JsonKeys.CHAPTER]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val title =
                            attrs[MangaDexSite.JsonKeys.TITLE]?.let {
                                if (it is JsonNull) "" else it.jsonPrimitive.content
                            } ?: ""
                        val id = item.jsonObject[MangaDexSite.JsonKeys.ID]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val number = chapterNum.toFloatOrNull() ?: return@mapNotNull null

                        Chapter(
                            id = id,
                            number = number,
                            name = MangaDexSite.chapterName(chapterNum, title),
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

    override suspend fun getPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    client.get(MangaDexSite.Endpoints.atHomeServer(chapterId)) {
                        header(MangaDexSite.Headers.USER_AGENT_KEY, MangaDexSite.USER_AGENT)
                    }
                val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val baseUrl = root[MangaDexSite.JsonKeys.BASE_URL]?.jsonPrimitive?.content ?: return@withContext emptyList()
                val chapterObj = root[MangaDexSite.JsonKeys.CHAPTER]?.jsonObject ?: return@withContext emptyList()
                val hash = chapterObj[MangaDexSite.JsonKeys.HASH]?.jsonPrimitive?.content ?: return@withContext emptyList()

                // Prefer full quality, fall back to data-saver
                val fullData = chapterObj[MangaDexSite.JsonKeys.DATA]?.jsonArray
                val saverData = chapterObj[MangaDexSite.JsonKeys.DATA_SAVER]?.jsonArray
                val pages = if (fullData != null && fullData.isNotEmpty()) fullData else saverData
                val quality =
                    if (fullData != null && fullData.isNotEmpty()) {
                        MangaDexSite.Quality.DATA
                    } else {
                        MangaDexSite.Quality.DATA_SAVER
                    }

                pages?.mapIndexed { index, page ->
                    MangaPage(
                        index = index,
                        imageUrl = MangaDexSite.Endpoints.page(baseUrl, quality, hash, page.jsonPrimitive.content),
                    )
                } ?: emptyList()
            } catch (e: Exception) {
                reportParserIssue("getPages", e, mapOf("chapterId" to chapterId))
                emptyList()
            }
        }

    private fun reportParserIssue(
        method: String,
        throwable: Throwable,
        extras: Map<String, String> = emptyMap(),
    ) {
        logger.reportError(area = "MangaDexParser", method = method, throwable = throwable, extras = extras)
    }
}

/**
 * MangaDex API contract — endpoint composition, query params, JSON keys, and
 * response tokens. Grouped here so an upstream API change is a one-place fix
 * rather than scattered magic strings the catch path would swallow silently.
 */
internal object MangaDexSite {
    private const val API = "https://api.mangadex.org"
    private const val COVERS_HOST = "https://uploads.mangadex.org"
    const val USER_AGENT = "Miyo/2.0"
    const val DEFAULT_LANGUAGE = "en"

    object Endpoints {
        fun mangaSearch(): String = "$API/manga"

        fun feed(mangaId: String): String = "$API/manga/$mangaId/feed"

        fun atHomeServer(chapterId: String): String = "$API/at-home/server/$chapterId"

        fun cover(
            mangaId: String,
            fileName: String,
        ): String = "$COVERS_HOST/covers/$mangaId/$fileName.256.jpg"

        fun page(
            baseUrl: String,
            quality: String,
            hash: String,
            fileName: String,
        ): String = "$baseUrl/$quality/$hash/$fileName"
    }

    object Headers {
        const val USER_AGENT_KEY = "User-Agent"
    }

    object Params {
        const val LIMIT = "limit"
        const val TITLE = "title"
        const val ORDER_RELEVANCE = "order[relevance]"
        const val ORDER_CHAPTER = "order[chapter]"
        const val INCLUDES = "includes[]"
        const val TRANSLATED_LANGUAGE = "translatedLanguage[]"
    }

    object Defaults {
        const val SEARCH_LIMIT = "25"
        const val FEED_LIMIT = "500"
        const val ORDER_DESC = "desc"
        const val ORDER_ASC = "asc"
    }

    object Includes {
        const val COVER_ART = "cover_art"
        const val SCANLATION_GROUP = "scanlation_group"
    }

    /** Image quality segment inserted into the page URL path. */
    object Quality {
        const val DATA = "data"
        const val DATA_SAVER = "data-saver"
    }

    object JsonKeys {
        const val DATA = "data"
        const val ID = "id"
        const val ATTRIBUTES = "attributes"
        const val TITLE = "title"
        const val RELATIONSHIPS = "relationships"
        const val LAST_CHAPTER = "lastChapter"
        const val EXTERNAL_URL = "externalUrl"
        const val CHAPTER = "chapter"
        const val BASE_URL = "baseUrl"
        const val HASH = "hash"
        const val DATA_SAVER = "dataSaver"
    }

    object Patterns {
        /** Captures the `fileName` value from the stringified `relationships` JSON array. */
        val COVER_FILE_NAME = Regex("""(?<="fileName":").+?(?=")""")
    }

    /** "Ch. N" or "Ch. N - Title" — matches the MangaPill side's chapter-name format. */
    fun chapterName(
        number: String,
        title: String,
    ): String = if (title.isNotEmpty()) "Ch. $number - $title" else "Ch. $number"
}
