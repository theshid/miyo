package ani.saikou.data.source.anime

import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.source.AnimeProvider
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Anime stream-URL parser for anizone.to. Independent of the Gogo family —
 * the source can fall back to this when anineko is unreachable (Cloudflare,
 * outage). See [`AnizoneSite`] for the scraping contract.
 *
 * Layout of the three calls:
 * - **search**: GET `/anime?search=Q` (shell), then POST to `/livewire/update`
 *   with the captured snapshot + CSRF + cookies to receive the hydrated list.
 * - **getEpisodes**: GET `/anime/{slug}` (SSR) and harvest the episode anchors.
 * - **getStreamLinks**: GET `/anime/{slug}/{N}` (SSR) and pull the m3u8 from
 *   the `<source>` tag.
 *
 * Lives in androidMain — Jsoup is JVM-only. Failures use the same typed
 * [`AnimeSourceFailure`] hierarchy as [`GogoParser`] so the repository can
 * treat all providers uniformly.
 */
class AnizoneParser(
    private val logger: Logger,
) : AnimeProvider {
    override val id: String = "anizone"
    override val host: String = AnizoneSite.HOST_NAME
    override val displayName: String = "Anizone"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    override suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>> =
        withContext(Dispatchers.IO) {
            // Phase 1 — fetch the shell page to grab CSRF + session cookie + list snapshot.
            val shellUrl = AnizoneSite.Paths.searchEntryPage(query)
            val shellResult =
                executeRequest(
                    url = shellUrl,
                    stage = STAGE_SEARCH,
                    extras = mapOf("query" to query),
                )
                    ?: return@withContext AnimeSourceResult.Failed(
                        AnimeSourceFailure.TransportError(IOException("shell request returned no response")),
                    )
            shellResult.failure?.let { return@withContext AnimeSourceResult.Failed(it) }

            val shellDoc =
                shellResult.document ?: return@withContext AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_SEARCH))
            val csrf =
                shellDoc.selectFirst(AnizoneSite.Selectors.CSRF_META)?.attr("content")
                    ?: return@withContext anizoneContractChange(STAGE_SEARCH, "csrf-token missing", mapOf("query" to query))
            val searchSnapshot =
                shellDoc
                    .select("[${AnizoneSite.Selectors.WIRE_SNAPSHOT_ATTR}]")
                    .firstOrNull { it.attr(AnizoneSite.Selectors.WIRE_SNAPSHOT_ATTR).contains("\"search\":") }
                    ?.attr(AnizoneSite.Selectors.WIRE_SNAPSHOT_ATTR)
                    ?: return@withContext anizoneContractChange(STAGE_SEARCH, "search wire:snapshot missing", mapOf("query" to query))

            // Phase 2 — POST the Livewire update with the same session cookies.
            val livewireBody = buildLivewireBody(csrf, searchSnapshot).toString()
            val livewireResult =
                executeRequest(
                    url = AnizoneSite.Paths.livewireUpdate(),
                    stage = STAGE_SEARCH,
                    extras = mapOf("query" to query, "phase" to "livewire"),
                    method = Connection.Method.POST,
                    body = livewireBody,
                    additionalHeaders =
                        mapOf(
                            "Content-Type" to "application/json",
                            "X-CSRF-TOKEN" to csrf,
                            "X-Livewire" to "1",
                            "Referer" to shellUrl,
                        ),
                    cookies = shellResult.cookies,
                )
                    ?: return@withContext AnimeSourceResult.Failed(
                        AnimeSourceFailure.TransportError(IOException("livewire POST returned no response")),
                    )
            livewireResult.failure?.let { return@withContext AnimeSourceResult.Failed(it) }

            val responseBody =
                livewireResult.rawBody
                    ?: return@withContext anizoneContractChange(STAGE_SEARCH, "livewire empty body", mapOf("query" to query))
            extractSearchResults(responseBody, mapOf("query" to query))
        }

    override suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>> =
        withContext(Dispatchers.IO) {
            val result =
                executeRequest(
                    url = AnizoneSite.Paths.anime(slug),
                    stage = STAGE_EPISODES,
                    extras = mapOf("slug" to slug),
                )
                    ?: return@withContext AnimeSourceResult.Failed(
                        AnimeSourceFailure.TransportError(IOException("episode-list request returned no response")),
                    )
            result.failure?.let { return@withContext AnimeSourceResult.Failed(it) }
            val doc = result.document ?: return@withContext AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_EPISODES))

            val anchors = doc.select(AnizoneSite.Selectors.EPISODE_LINK_ON_DETAIL)
            val episodes = mutableListOf<Episode>()
            val seen = mutableSetOf<String>()
            for (anchor in anchors) {
                val href = anchor.attr("href")
                val match = AnizoneSite.Patterns.EPISODE_URL.find(href) ?: continue
                val anchorSlug = match.groupValues[1]
                if (anchorSlug != slug) continue
                val number = match.groupValues[2]
                if (!seen.add(number)) continue
                episodes.add(Episode(number = number, link = href))
            }
            if (episodes.isEmpty()) {
                logSelectorMiss(STAGE_EPISODES, AnizoneSite.Selectors.EPISODE_LINK_ON_DETAIL, mapOf("slug" to slug))
                return@withContext AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_EPISODES))
            }
            AnimeSourceResult.Success(episodes.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE })
        }

    override suspend fun getStreamLinks(episodeUrl: String): AnimeSourceResult<List<StreamLink>> =
        withContext(Dispatchers.IO) {
            val result =
                executeRequest(
                    url = episodeUrl,
                    stage = STAGE_STREAMS,
                    extras = mapOf("episodeUrl" to episodeUrl),
                )
                    ?: return@withContext AnimeSourceResult.Failed(
                        AnimeSourceFailure.TransportError(IOException("episode-page request returned no response")),
                    )
            result.failure?.let { return@withContext AnimeSourceResult.Failed(it) }
            val doc = result.document ?: return@withContext AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_STREAMS))

            // Prefer a real <source> element under the player; fall back to a regex
            // sweep of the raw HTML in case the markup shifts.
            val streamUrls = mutableListOf<String>()
            for (source in doc.select(AnizoneSite.Selectors.VIDEO_SOURCE_TAG)) {
                val src = source.attr("src")
                if (src.isNotBlank() && (src.contains(".m3u8") || src.contains(".mp4"))) {
                    streamUrls.add(src)
                }
            }
            if (streamUrls.isEmpty()) {
                AnizoneSite.Patterns.M3U8_URL
                    .findAll(doc.outerHtml())
                    .forEach { streamUrls.add(it.value) }
            }
            if (streamUrls.isEmpty()) {
                AnizoneSite.Patterns.MP4_URL
                    .findAll(doc.outerHtml())
                    .forEach { streamUrls.add(it.value) }
            }

            if (streamUrls.isEmpty()) {
                logSelectorMiss(STAGE_STREAMS, AnizoneSite.Selectors.VIDEO_SOURCE_TAG, mapOf("episodeUrl" to episodeUrl))
                return@withContext AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_STREAMS))
            }

            // anizone HLS CDNs (seiryuu, vid-cdn) reject requests without a Referer.
            val referer = "${AnizoneSite.HOST}/"
            val headers = mapOf("Referer" to referer)
            val links =
                streamUrls.distinct().map { url ->
                    StreamLink(
                        server = "Anizone",
                        url = url,
                        quality = AnizoneSite.DEFAULT_QUALITY,
                        headers = headers,
                        subtitles = emptyList(),
                    )
                }
            AnimeSourceResult.Success(links)
        }

    // ───────────────────────────────────────────────────────────────────────
    // Livewire body construction
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Build the JSON envelope Livewire expects on /livewire/update. The
     * `snapshot` field carries the raw JSON STRING (escaped) that came back
     * in the HTML's `wire:snapshot` attribute — Livewire round-trips it
     * unchanged.
     */
    private fun buildLivewireBody(
        csrf: String,
        snapshot: String,
    ): JsonObject =
        buildJsonObject {
            put("_token", JsonPrimitive(csrf))
            put(
                "components",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("snapshot", JsonPrimitive(snapshot))
                            put("updates", JsonObject(emptyMap()))
                            put("calls", JsonArray(emptyList()))
                        },
                    )
                },
            )
        }

    // ───────────────────────────────────────────────────────────────────────
    // Search-result extraction from the Livewire response body
    // ───────────────────────────────────────────────────────────────────────

    internal fun extractSearchResults(
        livewireResponseBody: String,
        extras: Map<String, String>,
    ): AnimeSourceResult<List<AnimeSearchResult>> {
        val parsed =
            runCatching { json.parseToJsonElement(livewireResponseBody).jsonObject }
                .getOrElse {
                    return anizoneContractChange(STAGE_SEARCH, "livewire JSON unparsable", extras)
                }
        val componentHtml =
            runCatching {
                parsed["components"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("effects")
                    ?.jsonObject
                    ?.get("html")
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
                ?: return anizoneContractChange(STAGE_SEARCH, "livewire effects.html missing", extras)

        // Sentinel from the rendered search component when 0 matches.
        if (componentHtml.contains("Nothing found", ignoreCase = true)) {
            return AnimeSourceResult.Success(emptyList())
        }

        val doc = Jsoup.parse(componentHtml)
        val anchors = doc.select("a[href]")
        val results = mutableListOf<AnimeSearchResult>()
        val seenSlugs = mutableSetOf<String>()
        for (anchor in anchors) {
            val href = anchor.attr("href")
            val match = AnizoneSite.Patterns.ANIME_DETAIL_URL.find(href) ?: continue
            val slug = match.groupValues[1]
            if (!seenSlugs.add(slug)) continue
            val title = resolveCardTitle(anchor) ?: continue
            val cover = nearestImageSrc(anchor)
            results.add(AnimeSearchResult(slug = slug, name = title, cover = cover))
        }

        if (results.isEmpty()) {
            logSelectorMiss(STAGE_SEARCH, "anime-detail anchors in livewire html", extras)
            // Empty Livewire body usually means catalog match was actually 0;
            // we already short-circuited above on "Nothing found", so reaching
            // here implies contract drift in the card markup.
            return AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_SEARCH))
        }
        return AnimeSourceResult.Success(results)
    }

    /**
     * Walks from the anime anchor up to its surrounding `x-data` block, then
     * pulls a readable title out of the `anmTitles: JSON.parse('…')` payload.
     * Picks the lowest-numbered key, which Alpine treats as the primary
     * variant.
     *
     * Returns null when no title can be recovered — the caller drops the
     * result rather than showing a blank picker entry.
     */
    private fun resolveCardTitle(anchor: Element): String? {
        // Walk up the DOM looking for an x-data attribute carrying anmTitles.
        var current: Element? = anchor
        while (current != null) {
            val xdata = current.attr("x-data")
            if (xdata.contains("anmTitles")) {
                val match = AnizoneSite.Patterns.ANM_TITLES_JSON.find(xdata) ?: return null
                val rawJsonLiteral = match.groupValues[1]
                val decoded =
                    runCatching { decodeJsUnicodeEscapes(rawJsonLiteral) }
                        .getOrNull() ?: return null
                val titles =
                    runCatching { json.parseToJsonElement(decoded).jsonObject }
                        .getOrNull() ?: return null
                // Pick the lowest-numbered key.
                val ordered =
                    titles.entries
                        .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.jsonPrimitive.content } }
                        .sortedBy { it.first }
                return ordered.firstOrNull()?.second?.takeIf { it.isNotBlank() }
            }
            current = current.parent()
        }
        // Fallback: inner span text — usually empty since Alpine renders it.
        val span = anchor.selectFirst("span[x-text=displayAnimeTitle]")
        return span?.text()?.takeIf { it.isNotBlank() }
    }

    private fun nearestImageSrc(anchor: Element): String {
        var current: Element? = anchor
        while (current != null) {
            val img = current.selectFirst("img[src]")
            if (img != null) {
                val src = img.attr("src")
                if (src.isNotBlank()) return src
            }
            current = current.parent()
        }
        return ""
    }

    /** Decodes a JS-style string with embedded `\\uXXXX` escapes into real characters. */
    private fun decodeJsUnicodeEscapes(input: String): String {
        val regex = Regex("""\\u([0-9A-Fa-f]{4})""")
        return regex.replace(input) { match ->
            val codepoint = match.groupValues[1].toInt(16)
            codepoint.toChar().toString()
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // HTTP helper
    // ───────────────────────────────────────────────────────────────────────

    private data class ExecuteResult(
        val document: Document?,
        val rawBody: String?,
        val cookies: Map<String, String>,
        val failure: AnimeSourceFailure?,
    )

    private fun executeRequest(
        url: String,
        stage: String,
        extras: Map<String, String>,
        method: Connection.Method = Connection.Method.GET,
        body: String? = null,
        additionalHeaders: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): ExecuteResult? {
        return try {
            val connection =
                Jsoup
                    .connect(url)
                    .userAgent(AnizoneSite.USER_AGENT)
                    .method(method)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .maxBodySize(0) // no truncation
            for ((k, v) in additionalHeaders) connection.header(k, v)
            connection.cookies(cookies)
            if (body != null) connection.requestBody(body)

            val response = connection.execute()
            val status = response.statusCode()
            val rawBody = response.body()
            val headerLookup: (String) -> String? = { name -> response.header(name) }

            if (CloudflareDetector.isChallenge(status, headerLookup, rawBody)) {
                val cfRay = CloudflareDetector.cfRay(headerLookup)
                logger.reportWarning(
                    area = AREA,
                    method = stage,
                    message = "Cloudflare challenge detected",
                    extras = extras + mapOf("status" to status.toString(), "cf-ray" to (cfRay ?: "(none)")),
                )
                return ExecuteResult(null, null, emptyMap(), AnimeSourceFailure.Blocked(status, cfRay))
            }
            if (status !in 200..299) {
                logger.reportWarning(
                    area = AREA,
                    method = stage,
                    message = "non-success HTTP status",
                    extras = extras + mapOf("status" to status.toString()),
                )
                return ExecuteResult(null, null, response.cookies(), AnimeSourceFailure.Unavailable(status))
            }
            val doc = Jsoup.parse(rawBody, url)
            ExecuteResult(doc, rawBody, response.cookies(), null)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            logger.reportError(area = AREA, method = stage, throwable = e, extras = extras)
            ExecuteResult(null, null, emptyMap(), AnimeSourceFailure.TransportError(e))
        } catch (e: Exception) {
            logger.reportError(area = AREA, method = stage, throwable = e, extras = extras)
            ExecuteResult(null, null, emptyMap(), AnimeSourceFailure.TransportError(e))
        }
    }

    private fun anizoneContractChange(
        stage: String,
        message: String,
        extras: Map<String, String>,
    ): AnimeSourceResult<Nothing> {
        logger.reportWarning(area = AREA, method = stage, message = message, extras = extras)
        return AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(stage))
    }

    private fun logSelectorMiss(
        stage: String,
        selector: String,
        extras: Map<String, String>,
    ) {
        logger.reportWarning(
            area = AREA,
            method = stage,
            message = "selector matched 0 elements — possible markup change",
            extras = extras + mapOf("selector" to selector, "host" to AnizoneSite.HOST_NAME),
        )
    }

    private companion object {
        private const val AREA = "AnizoneParser"
        private const val STAGE_SEARCH = "search"
        private const val STAGE_EPISODES = "getEpisodes"
        private const val STAGE_STREAMS = "getStreamLinks"
        private const val REQUEST_TIMEOUT_MS = 15_000
    }
}
