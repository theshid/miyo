package ani.saikou.data.source.anime

import android.net.Uri
import ani.saikou.domain.model.AnimeSearchResult
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.SubtitleTrack
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Anime stream-URL parser scraping anineko.to (the post-anitaku rebrand of
 * GogoAnime). Three-step pipeline: search → episode list → embed URL →
 * direct stream.
 *
 * Endpoint paths, CSS selectors, regex patterns, and site-stamped tokens
 * all live in [GogoSite] so a markup change is a one-place fix.
 *
 * Lives in androidMain — Jsoup is JVM-only, [Uri] is Android-specific.
 * Mirrors the [ani.saikou.data.source.manga.MangaPillParser] pattern.
 */
class GogoParser(
    private val logger: Logger,
) {
    suspend fun search(query: String): List<AnimeSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    Jsoup
                        .connect(GogoSite.Paths.search(query))
                        .userAgent(GogoSite.USER_AGENT)
                        .timeout(10000)
                        .get()

                doc.select(GogoSite.Selectors.SEARCH_RESULTS).map { el: Element ->
                    val img = el.selectFirst("img")
                    AnimeSearchResult(
                        slug = el.attr("href").removePrefix(GogoSite.Tokens.CATEGORY_PREFIX),
                        name = img?.attr("alt").orEmpty(),
                        cover = img?.attr("src").orEmpty(),
                    )
                }
            } catch (e: Exception) {
                reportParserIssue("search", e, mapOf("query" to query))
                emptyList()
            }
        }

    suspend fun getEpisodes(slug: String): List<Episode> =
        withContext(Dispatchers.IO) {
            val episodes = mutableListOf<Episode>()
            try {
                val doc =
                    Jsoup
                        .connect(GogoSite.Paths.anime(slug))
                        .userAgent(GogoSite.USER_AGENT)
                        .timeout(10000)
                        .get()

                for (el in doc.select(GogoSite.Selectors.EPISODE_LINKS_PRIMARY)) {
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
            } catch (e: Exception) {
                reportParserIssue("getEpisodes", e, mapOf("slug" to slug))
            }
            episodes
        }

    suspend fun getStreamLinks(episodeLink: String): List<StreamLink> =
        withContext(Dispatchers.IO) {
            val links = mutableListOf<StreamLink>()
            try {
                val doc =
                    Jsoup
                        .connect(episodeLink)
                        .userAgent(GogoSite.USER_AGENT)
                        .ignoreHttpErrors(true)
                        .timeout(10000)
                        .get()

                // (serverName, embedUrl, subtitleTracks)
                val servers = mutableListOf<Triple<String, String, List<SubtitleTrack>>>()
                collectServers(doc.select(GogoSite.Selectors.SERVER_LINKS_PRIMARY), servers)

                for ((name, url, subs) in servers) {
                    val extracted = extractDirectLink(name, url, subs)
                    if (extracted != null) links.add(extracted)
                }
            } catch (e: Exception) {
                reportParserIssue("getStreamLinks", e, mapOf("episodeLink" to episodeLink))
            }
            links
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

    private fun reportParserIssue(
        method: String,
        throwable: Throwable,
        extras: Map<String, String> = emptyMap(),
    ) {
        logger.reportError(area = "GogoParser", method = method, throwable = throwable, extras = extras)
    }

    private fun extractDirectLink(
        name: String,
        url: String,
        subtitles: List<SubtitleTrack> = emptyList(),
    ): StreamLink? {
        return try {
            val page =
                Jsoup
                    .connect(url)
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .header("Referer", "${GogoSite.HOST}/")
                    .userAgent(GogoSite.USER_AGENT)
                    .timeout(10000)
                    .get()
                    .html()

            // Strip query params from Referer — CDNs reject long/messy Referer headers
            val cleanReferer = url.substringBefore("?")
            val headers = mapOf("Referer" to cleanReferer)

            // 1) Direct regex on raw HTML
            findStreamUrl(page)?.let { (streamUrl, _) ->
                return StreamLink(
                    server = name,
                    url = streamUrl,
                    quality = GogoSite.DEFAULT_QUALITY,
                    headers = headers,
                    subtitles = subtitles,
                )
            }

            // 2) Unpack eval(function(p,a,c,k,e,d){...}) obfuscated JS
            val unpacked = unpackJsPacked(page) ?: return null
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
        } catch (e: Exception) {
            reportParserIssue("extractDirectLink", e, mapOf("server" to name, "url" to url))
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
            reportParserIssue("parseSubtitlesFromUrl", e, mapOf("embedUrl" to embedUrl))
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
            reportParserIssue("parseSubtitlesFromUnpacked", e)
            emptyList()
        }

    private fun httpsIfy(text: String): String = if (text.startsWith("//")) "https:$text" else text
}
