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
 * Anime stream-URL parser scraping anitaku.to (a GogoAnime mirror).
 * Three-step pipeline: search → episode list → embed URL → direct stream.
 *
 * Lives in androidMain — Jsoup is JVM-only, [Uri] is Android-specific.
 * Mirrors the [ani.saikou.data.source.manga.MangaPillParser] pattern.
 */
class GogoParser(
    private val logger: Logger,
) {
    companion object {
        private const val HOST = "https://anitaku.to"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    suspend fun search(query: String): List<AnimeSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    Jsoup
                        .connect("$HOST/search.html?keyword=$query")
                        .userAgent(USER_AGENT)
                        .timeout(10000)
                        .get()

                doc.select(".last_episodes > ul > li div.img > a").map { el: Element ->
                    AnimeSearchResult(
                        slug = el.attr("href").replace("/category/", ""),
                        name = el.attr("title"),
                        cover = el.select("img").attr("src"),
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
                        .connect("$HOST/category/$slug")
                        .userAgent(USER_AGENT)
                        .timeout(10000)
                        .get()

                // New anitaku.to structure
                val episodeLinks = doc.select("ul#episode_related a[href], ul.ep-range a[href]")
                if (episodeLinks.isNotEmpty()) {
                    for (el in episodeLinks.reversed()) {
                        val href = el.attr("href").trim()
                        if (!href.contains("-episode-")) continue
                        val num =
                            el.attr("data-num").ifEmpty {
                                el.select(".name").text().replace("EP", "").trim().ifEmpty {
                                    Regex("""-episode-(\d+)""").find(href)?.groupValues?.get(1) ?: ""
                                }
                            }
                        if (num.isNotEmpty()) {
                            val fullLink = if (href.startsWith("http")) href else "$HOST$href"
                            episodes.add(Episode(number = num, link = fullLink))
                        }
                    }
                }

                // Fallback: AJAX method
                if (episodes.isEmpty()) {
                    val lastEpisode = doc.select("ul#episode_page > li:last-child > a").attr("ep_end")
                    val animeId = doc.select("input#movie_id").attr("value")
                    if (lastEpisode.isNotEmpty() && animeId.isNotEmpty()) {
                        val ajax =
                            Jsoup
                                .connect(
                                    "https://ajax.gogocdn.net/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId",
                                ).userAgent(USER_AGENT)
                                .timeout(10000)
                                .get()

                        for (el in ajax.select("ul > li > a").reversed()) {
                            val num =
                                el
                                    .select(".name")
                                    .text()
                                    .replace("EP", "")
                                    .trim()
                            episodes.add(Episode(number = num, link = HOST + el.attr("href").trim()))
                        }
                    }
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
                        .userAgent(USER_AGENT)
                        .ignoreHttpErrors(true)
                        .timeout(10000)
                        .get()

                // (serverName, embedUrl, subtitleTracks)
                val servers = mutableListOf<Triple<String, String, List<SubtitleTrack>>>()

                // New structure
                for (el in doc.select("li.server a.server-video[data-video]")) {
                    val videoUrl = el.attr("data-video")
                    val serverName = el.text().replace("Choose this server", "").trim()
                    if (videoUrl.isNotEmpty() && serverName.isNotEmpty()) {
                        val subs = parseSubtitlesFromUrl(videoUrl)
                        servers.add(Triple(serverName, httpsIfy(videoUrl), subs))
                    }
                }

                // Fallback
                if (servers.isEmpty()) {
                    for (el in doc.select("div.anime_muti_link > ul > li:not(li.anime) a[data-video]")) {
                        val videoUrl = el.attr("data-video")
                        val serverName = el.text().replace("Choose this server", "").trim()
                        if (videoUrl.isNotEmpty() && serverName.isNotEmpty()) {
                            val subs = parseSubtitlesFromUrl(videoUrl)
                            servers.add(Triple(serverName, httpsIfy(videoUrl), subs))
                        }
                    }
                }

                for ((name, url, subs) in servers) {
                    val extracted = extractDirectLink(name, url, subs)
                    if (extracted != null) links.add(extracted)
                }
            } catch (e: Exception) {
                reportParserIssue("getStreamLinks", e, mapOf("episodeLink" to episodeLink))
            }
            links
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
                    .header("Referer", "$HOST/")
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .get()
                    .html()

            // Strip query params from Referer — CDNs reject long/messy Referer headers
            val cleanReferer = url.substringBefore("?")
            val headers = mapOf("Referer" to cleanReferer)

            // 1) Direct regex on raw HTML
            val m3u8 = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""").find(page)?.value
            if (m3u8 != null) {
                return StreamLink(server = name, url = m3u8, quality = "Auto", headers = headers, subtitles = subtitles)
            }

            val mp4 = Regex("""(https?://[^\s"'\\]+\.mp4[^\s"'\\]*)""").find(page)?.value
            if (mp4 != null) {
                return StreamLink(server = name, url = mp4, quality = "Auto", headers = headers, subtitles = subtitles)
            }

            // 2) Unpack eval(function(p,a,c,k,e,d){...}) obfuscated JS
            val unpacked = unpackJsPacked(page) ?: return null
            // Prefer URL-derived subs; fall back to scanning the unpacked JS for VTT tracks.
            val allSubs = if (subtitles.isNotEmpty()) subtitles else parseSubtitlesFromUnpacked(unpacked)

            val unpackedM3u8 = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""").find(unpacked)?.value
            if (unpackedM3u8 != null) {
                return StreamLink(server = name, url = unpackedM3u8, quality = "Auto", headers = headers, subtitles = allSubs)
            }
            val unpackedMp4 = Regex("""(https?://[^\s"'\\]+\.mp4[^\s"'\\]*)""").find(unpacked)?.value
            if (unpackedMp4 != null) {
                return StreamLink(server = name, url = unpackedMp4, quality = "Auto", headers = headers, subtitles = allSubs)
            }
            null
        } catch (e: Exception) {
            reportParserIssue("extractDirectLink", e, mapOf("server" to name, "url" to url))
            null
        }
    }

    /**
     * Unpacks Dean Edwards' p.a.c.k.e.d JavaScript — the obfuscation used by
     * most GogoAnime embed servers to hide stream URLs.
     */
    private fun unpackJsPacked(html: String): String? {
        // Match eval(function(p,a,c,k,e,d){...}('payload',base,count,'keys'.split('|')))
        val packed =
            Regex(
                """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)""",
            ).find(html) ?: return null

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

    /**
     * Extracts subtitle tracks from unpacked JWPlayer config.
     * Looks for VTT URLs in the JS source.
     */
    private fun parseSubtitlesFromUnpacked(js: String): List<SubtitleTrack> =
        try {
            val vttPattern = Regex("""(https?://[^\s"'\\]+\.vtt[^\s"'\\]*)""")
            vttPattern
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
