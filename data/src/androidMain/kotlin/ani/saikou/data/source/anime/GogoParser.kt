package ani.saikou.data.source.anime

import android.net.Uri
import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.SubtitleTrack
import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.domain.model.anime.fold
import ani.saikou.domain.source.AnimeProvider
import ani.saikou.domain.source.CloudflareClearance
import ani.saikou.domain.source.CloudflareClearanceProvider
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Anime stream-URL parser scraping anineko.to (the post-anitaku rebrand of
 * GogoAnime). Three-step pipeline: search → episode list → embed URL →
 * direct stream.
 *
 * Endpoint paths, CSS selectors, regex patterns, and site-stamped tokens
 * all live in [GogoSite] so a markup change is a one-place fix.
 *
 * Responses are inspected (status + headers + body sniff) BEFORE parsing,
 * so the parser distinguishes a Cloudflare challenge from a legitimate
 * empty result. Failures are surfaced as typed [`AnimeSourceFailure`] —
 * never swallowed into empty lists, because the UI used to mislabel
 * outages as "anime not found" when the source had simply rejected us.
 *
 * Lives in androidMain — Jsoup is JVM-only, [Uri] is Android-specific.
 * Mirrors the [ani.saikou.data.source.manga.MangaPillParser] pattern.
 */
class GogoParser(
    private val logger: Logger,
    private val clearanceProvider: CloudflareClearanceProvider? = null,
) : AnimeProvider {
    override val id: String = "anineko"
    override val host: String = "anineko.to"
    override val displayName: String = "Anineko"
    override val cloudflareHosts: Set<String> = setOf("anineko.to")

    override suspend fun search(query: String): AnimeSourceResult<List<AnimeSearchResult>> =
        withContext(Dispatchers.IO) {
            fetchDocument(
                url = GogoSite.Paths.search(query),
                stage = STAGE_SEARCH,
                extras = mapOf("query" to query),
            ).fold(
                onFailure = { AnimeSourceResult.Failed(it) },
                onSuccess = { fetched -> parseSearchResults(fetched, mapOf("query" to query)) },
            )
        }

    override suspend fun getEpisodes(slug: String): AnimeSourceResult<List<Episode>> =
        withContext(Dispatchers.IO) {
            fetchDocument(
                url = GogoSite.Paths.anime(slug),
                stage = STAGE_EPISODES,
                extras = mapOf("slug" to slug),
            ).fold(
                onFailure = { AnimeSourceResult.Failed(it) },
                onSuccess = { fetched -> parseEpisodes(fetched, mapOf("slug" to slug)) },
            )
        }

    override suspend fun getStreamLinks(episodeLink: String): AnimeSourceResult<List<StreamLink>> =
        withContext(Dispatchers.IO) {
            fetchDocument(
                url = episodeLink,
                stage = STAGE_STREAMS,
                extras = mapOf("episodeLink" to episodeLink),
            ).fold(
                onFailure = { AnimeSourceResult.Failed(it) },
                onSuccess = { fetched -> parseStreams(fetched, mapOf("episodeLink" to episodeLink)) },
            )
        }

    /**
     * Parse search-result cards out of a known-good response. Pure function
     * over the [Document] so the failure-mode tests don't need a network mock.
     *
     * Note: zero matches here is AMBIGUOUS — could be a legitimate empty
     * search result or markup drift. We emit a Sentry warning but still
     * return `Success(emptyList())` so the UI can render "no results found"
     * truthfully for benign empty searches.
     */
    internal fun parseSearchResults(
        fetched: FetchedResponse,
        extras: Map<String, String>,
    ): AnimeSourceResult<List<AnimeSearchResult>> {
        val matches = fetched.document.select(GogoSite.Selectors.SEARCH_RESULTS)
        if (matches.isEmpty()) {
            logSelectorMiss(STAGE_SEARCH, GogoSite.Selectors.SEARCH_RESULTS, fetched, extras)
        }
        return AnimeSourceResult.Success(
            matches.map { el ->
                val img = el.selectFirst("img")
                AnimeSearchResult(
                    slug = el.attr("href").removePrefix(GogoSite.Tokens.CATEGORY_PREFIX),
                    name = img?.attr("alt").orEmpty(),
                    cover = img?.attr("src").orEmpty(),
                )
            },
        )
    }

    /**
     * Parse episode anchors. Zero matches is treated as
     * [`AnimeSourceFailure.ContractChanged`] — a known anime page with no
     * episode anchors is a strong signal of markup drift, not a "0 episodes"
     * scenario (real "0 episodes" cases don't reach this codepath; the
     * source returns a 404 first).
     */
    internal fun parseEpisodes(
        fetched: FetchedResponse,
        extras: Map<String, String>,
    ): AnimeSourceResult<List<Episode>> {
        val anchors = fetched.document.select(GogoSite.Selectors.EPISODE_LINKS_PRIMARY)
        if (anchors.isEmpty()) {
            logSelectorMiss(STAGE_EPISODES, GogoSite.Selectors.EPISODE_LINKS_PRIMARY, fetched, extras)
            return AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_EPISODES))
        }
        val episodes = mutableListOf<Episode>()
        for (el in anchors) {
            val href = el.attr("href").trim()
            if (!href.contains(GogoSite.Tokens.EPISODE_PATH_FRAGMENT)) continue
            val num =
                GogoSite.Patterns.EPISODE_NUMBER
                    .find(href)
                    ?.groupValues
                    ?.get(1)
                    ?: continue
            episodes.add(Episode(number = num, link = GogoSite.Paths.absoluteUrl(href)))
        }
        // Anchors matched but every one was filtered out by the href shape
        // check — also a contract change.
        if (episodes.isEmpty()) {
            logSelectorMiss(STAGE_EPISODES, "post-filter[$STAGE_EPISODES]", fetched, extras + ("anchor_count" to anchors.size.toString()))
            return AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_EPISODES))
        }
        return AnimeSourceResult.Success(episodes)
    }

    /**
     * Parse the embed-server buttons + drive the embed-extraction loop.
     * Zero buttons on an episode page is treated as
     * [`AnimeSourceFailure.ContractChanged`] — the markup expects every
     * episode page to advertise at least one embed.
     *
     * Per-embed failures don't fail the whole call; each is logged with
     * its server name and either silently skipped or surfaced via the
     * eventual empty-list outcome.
     */
    internal fun parseStreams(
        fetched: FetchedResponse,
        extras: Map<String, String>,
    ): AnimeSourceResult<List<StreamLink>> {
        val serverButtons = fetched.document.select(GogoSite.Selectors.SERVER_LINKS_PRIMARY)
        if (serverButtons.isEmpty()) {
            logSelectorMiss(STAGE_STREAMS, GogoSite.Selectors.SERVER_LINKS_PRIMARY, fetched, extras)
            return AnimeSourceResult.Failed(AnimeSourceFailure.ContractChanged(STAGE_STREAMS))
        }
        val servers = mutableListOf<Triple<String, String, List<SubtitleTrack>>>()
        collectServers(serverButtons, servers)
        val links = mutableListOf<StreamLink>()
        for ((name, url, subs) in servers) {
            val extracted = extractDirectLink(name, url, subs)
            if (extracted != null) links.add(extracted)
        }
        return AnimeSourceResult.Success(links)
    }

    /**
     * Wrapper around the fetched document + the response metadata we want
     * available downstream (status, cf-ray, host) so the selector-zero
     * telemetry knows where the data came from.
     */
    internal data class FetchedResponse(
        val document: Document,
        val status: Int,
        val host: String,
        val cfRay: String?,
    )

    /**
     * Single point that performs the network round-trip + classifies the
     * response. Returns the parsed [`FetchedResponse`] on success, or a
     * typed [`AnimeSourceFailure`] when something blocked us.
     *
     * Centralising this means every entry point gets the same Cloudflare
     * detection, status handling, and telemetry — no chance for one path
     * to silently swallow a challenge.
     */
    private suspend fun fetchDocument(
        url: String,
        stage: String,
        extras: Map<String, String>,
    ): AnimeSourceResult<FetchedResponse> {
        val host = safeHost(url)
        val needsCf = cloudflareHosts.any { host.equals(it, ignoreCase = true) }

        var clearance: CloudflareClearance? =
            if (needsCf) clearanceProvider?.getClearance(host) else null

        repeat(MAX_FETCH_ATTEMPTS) { attempt ->
            val result = executeOnce(url, stage, extras, clearance)
            when (result) {
                is FetchAttempt.Outcome -> return result.result
                is FetchAttempt.Challenged -> {
                    val isLastAttempt = attempt == MAX_FETCH_ATTEMPTS - 1
                    if (isLastAttempt || !needsCf || clearanceProvider == null) {
                        return AnimeSourceResult.Failed(AnimeSourceFailure.Blocked(result.status, result.cfRay))
                    }
                    // First-attempt challenge: cached clearance was stale or
                    // we had none. Force a fresh solve, then loop.
                    clearanceProvider.invalidate(host)
                    clearance = clearanceProvider.getClearance(host)
                    if (clearance == null) {
                        return AnimeSourceResult.Failed(AnimeSourceFailure.Blocked(result.status, result.cfRay))
                    }
                }
            }
        }
        // Defensive fallback — repeat blocks shouldn't fall through, but Kotlin
        // can't prove that without an explicit return.
        return AnimeSourceResult.Failed(AnimeSourceFailure.Blocked(0, null))
    }

    private sealed interface FetchAttempt {
        data class Outcome(
            val result: AnimeSourceResult<FetchedResponse>,
        ) : FetchAttempt

        data class Challenged(
            val status: Int,
            val cfRay: String?,
        ) : FetchAttempt
    }

    private fun executeOnce(
        url: String,
        stage: String,
        extras: Map<String, String>,
        clearance: CloudflareClearance?,
    ): FetchAttempt {
        return try {
            val connection =
                Jsoup
                    .connect(url)
                    .userAgent(clearance?.userAgent ?: GogoSite.USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .ignoreHttpErrors(true) // classify status ourselves
                    .ignoreContentType(true)
            if (clearance != null) connection.cookies(clearance.cookies)
            val response = connection.execute()
            val status = response.statusCode()
            val body = response.body()
            val headerLookup: (String) -> String? = { name -> response.header(name) }
            val cfRay = CloudflareDetector.cfRay(headerLookup)
            val host = safeHost(url)

            if (CloudflareDetector.isChallenge(status, headerLookup, body)) {
                logger.reportWarning(
                    area = AREA,
                    method = stage,
                    message = "Cloudflare challenge detected",
                    extras =
                        extras +
                            mapOf(
                                "host" to host,
                                "status" to status.toString(),
                                "cf-ray" to (cfRay ?: "(none)"),
                            ),
                )
                return FetchAttempt.Challenged(status, cfRay)
            }

            if (status !in 200..299) {
                logger.reportWarning(
                    area = AREA,
                    method = stage,
                    message = "non-success HTTP status",
                    extras = extras + mapOf("host" to host, "status" to status.toString()),
                )
                return FetchAttempt.Outcome(AnimeSourceResult.Failed(AnimeSourceFailure.Unavailable(status)))
            }

            FetchAttempt.Outcome(
                AnimeSourceResult.Success(
                    FetchedResponse(
                        document = Jsoup.parse(body, url),
                        status = status,
                        host = host,
                        cfRay = cfRay,
                    ),
                ),
            )
        } catch (ce: CancellationException) {
            // Coroutine cancellation must propagate untouched — Jsoup itself
            // is blocking and won't throw this today, but defence-in-depth
            // covers a future swap to a coroutine-aware engine.
            throw ce
        } catch (e: IOException) {
            logger.reportError(area = AREA, method = stage, throwable = e, extras = extras + ("host" to safeHost(url)))
            FetchAttempt.Outcome(AnimeSourceResult.Failed(AnimeSourceFailure.TransportError(e)))
        } catch (e: Exception) {
            // Defence in depth — Jsoup occasionally throws non-IO runtime errors
            // (malformed URI, charset issues). Bucket them as transport so the
            // UI still surfaces a network-style message rather than a generic crash.
            logger.reportError(area = AREA, method = stage, throwable = e, extras = extras + ("host" to safeHost(url)))
            FetchAttempt.Outcome(AnimeSourceResult.Failed(AnimeSourceFailure.TransportError(e)))
        }
    }

    private fun safeHost(url: String): String =
        runCatching { Uri.parse(url).host ?: url }
            .getOrDefault(url)

    private fun logSelectorMiss(
        stage: String,
        selector: String,
        fetched: FetchedResponse,
        extras: Map<String, String>,
    ) {
        logger.reportWarning(
            area = AREA,
            method = stage,
            message = "selector matched 0 elements — possible markup change",
            extras =
                extras +
                    mapOf(
                        "selector" to selector,
                        "match_count" to "0",
                        "host" to fetched.host,
                        "status" to fetched.status.toString(),
                        "cf-ray" to (fetched.cfRay ?: "(none)"),
                    ),
        )
    }

    private fun collectServers(
        elements: org.jsoup.select.Elements,
        sink: MutableList<Triple<String, String, List<SubtitleTrack>>>,
    ) {
        for (el in elements) {
            val videoUrl = el.attr("data-video")
            val serverName = el.text().replace(GogoSite.Tokens.SERVER_BUTTON_TEXT, "").trim()
            if (videoUrl.isNotEmpty() && serverName.isNotEmpty()) {
                val subs = parseSubtitlesFromUrl(videoUrl)
                sink.add(Triple(serverName, httpsIfy(videoUrl), subs))
            }
        }
    }

    private fun extractDirectLink(
        name: String,
        url: String,
        subtitles: List<SubtitleTrack> = emptyList(),
    ): StreamLink? {
        return try {
            val response =
                Jsoup
                    .connect(url)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .header("Referer", "${GogoSite.HOST}/")
                    .userAgent(GogoSite.USER_AGENT)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .execute()

            val embedStatus = response.statusCode()
            val embedBody = response.body()
            val headerLookup: (String) -> String? = { headerName -> response.header(headerName) }

            // Embed servers themselves can sit behind Cloudflare / fail with
            // 5xx / time out. Surface per-server failures as breadcrumbs so
            // the aggregate "no playable streams" doesn't hide the real cause.
            if (CloudflareDetector.isChallenge(embedStatus, headerLookup, embedBody)) {
                logger.reportWarning(
                    area = AREA,
                    method = "extractDirectLink",
                    message = "embed CF challenge",
                    extras =
                        mapOf(
                            "server" to name,
                            "host" to safeHost(url),
                            "status" to embedStatus.toString(),
                            "cf-ray" to (CloudflareDetector.cfRay(headerLookup) ?: "(none)"),
                        ),
                )
                return null
            }
            if (embedStatus !in 200..299) {
                logger.reportWarning(
                    area = AREA,
                    method = "extractDirectLink",
                    message = "embed non-success HTTP status",
                    extras = mapOf("server" to name, "host" to safeHost(url), "status" to embedStatus.toString()),
                )
                return null
            }

            // Strip query params from Referer — CDNs reject long/messy Referer headers
            val cleanReferer = url.substringBefore("?")
            val headers = mapOf("Referer" to cleanReferer)

            // 1) Direct regex on raw HTML
            findStreamUrl(embedBody)?.let { (streamUrl, _) ->
                return StreamLink(
                    server = name,
                    url = streamUrl,
                    quality = GogoSite.DEFAULT_QUALITY,
                    headers = headers,
                    subtitles = subtitles,
                )
            }

            // 2) Unpack eval(function(p,a,c,k,e,d){...}) obfuscated JS
            val unpacked = unpackJsPacked(embedBody) ?: return null
            // Prefer URL-derived subs; fall back to scanning the unpacked JS for VTT tracks.
            val allSubs = if (subtitles.isNotEmpty()) subtitles else parseSubtitlesFromUnpacked(unpacked)

            findStreamUrl(unpacked)?.let { (streamUrl, _) ->
                return StreamLink(
                    server = name,
                    url = streamUrl,
                    quality = GogoSite.DEFAULT_QUALITY,
                    headers = headers,
                    subtitles = allSubs,
                )
            }
            null
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            logger.reportError(
                area = AREA,
                method = "extractDirectLink",
                throwable = e,
                extras = mapOf("server" to name, "url" to url),
            )
            null
        }
    }

    /** Returns the first stream URL found in [body], paired with its container ("m3u8" or "mp4"),
     *  preferring HLS over progressive MP4. */
    private fun findStreamUrl(body: String): Pair<String, String>? {
        GogoSite.Patterns.M3U8_URL
            .find(body)
            ?.value
            ?.let { return it to "m3u8" }
        GogoSite.Patterns.MP4_URL
            .find(body)
            ?.value
            ?.let { return it to "mp4" }
        return null
    }

    /**
     * Unpacks Dean Edwards' p.a.c.k.e.d JavaScript — the obfuscation used by
     * most GogoAnime embed servers to hide stream URLs.
     */
    private fun unpackJsPacked(html: String): String? {
        // Match eval(function(p,a,c,k,e,d){...}('payload',base,count,'keys'.split('|')))
        val packed = GogoSite.Patterns.PACKED_JS.find(html) ?: return null

        val payload = packed.groupValues[1]
        val base = packed.groupValues[2].toIntOrNull() ?: return null
        val count = packed.groupValues[3].toIntOrNull() ?: return null
        val keys = packed.groupValues[4].split("|")

        var result = payload
        for (i in count - 1 downTo 0) {
            val encoded = i.toString(base)
            val replacement = if (i < keys.size && keys[i].isNotEmpty()) keys[i] else encoded
            result = result.replace(Regex("\\b${Regex.escape(encoded)}\\b"), replacement)
        }
        return result
    }

    /**
     * Extracts subtitle tracks from the embed URL's query parameters.
     * Format: ?caption_1=https://...vtt&sub_1=English&caption_2=...&sub_2=...
     */
    private fun parseSubtitlesFromUrl(embedUrl: String): List<SubtitleTrack> {
        val tracks = mutableListOf<SubtitleTrack>()
        try {
            val uri = Uri.parse(embedUrl)
            // Convention 1: indexed caption_X + sub_X (otakuhg.site, otakuvid.online).
            var i = 1
            while (true) {
                val captionUrl = uri.getQueryParameter("caption_$i") ?: break
                val label = uri.getQueryParameter("sub_$i") ?: "Track $i"
                tracks.add(SubtitleTrack(url = captionUrl, label = label))
                i++
            }
            // Convention 2: bare ?sub=<vtt-url> (vibeplayer.site). The URL itself
            // is the subtitle; no separate label key — default to English to
            // match parseSubtitlesFromUnpacked's convention.
            if (tracks.isEmpty()) {
                val bareSub = uri.getQueryParameter("sub")
                if (!bareSub.isNullOrBlank() && bareSub.startsWith("http", ignoreCase = true)) {
                    tracks.add(SubtitleTrack(url = bareSub, label = "English"))
                }
            }
        } catch (e: Exception) {
            logger.reportError(
                area = AREA,
                method = "parseSubtitlesFromUrl",
                throwable = e,
                extras = mapOf("embedUrl" to embedUrl),
            )
        }
        return tracks
    }

    /** Extracts subtitle tracks from unpacked JWPlayer config — VTT URLs in the JS source. */
    private fun parseSubtitlesFromUnpacked(js: String): List<SubtitleTrack> =
        try {
            GogoSite.Patterns.VTT_URL
                .findAll(js)
                .map { match -> SubtitleTrack(url = match.value, label = "English") }
                .distinctBy { it.url }
                .toList()
        } catch (e: Exception) {
            logger.reportError(area = AREA, method = "parseSubtitlesFromUnpacked", throwable = e)
            emptyList()
        }

    private fun httpsIfy(text: String): String = if (text.startsWith("//")) "https:$text" else text

    private companion object {
        private const val AREA = "GogoParser"
        private const val STAGE_SEARCH = "search"
        private const val STAGE_EPISODES = "getEpisodes"
        private const val STAGE_STREAMS = "getStreamLinks"
        private const val REQUEST_TIMEOUT_MS = 10_000
        private const val MAX_FETCH_ATTEMPTS = 2
    }
}
