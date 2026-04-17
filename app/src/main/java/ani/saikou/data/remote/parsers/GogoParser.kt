package ani.saikou.data.remote.parsers

import android.net.Uri
import android.util.Log
import ani.saikou.domain.model.AnimeSource
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
import ani.saikou.domain.model.SubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class GogoParser(private val dub: Boolean = false) {

    companion object {
        private const val HOST = "https://anitaku.to"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    suspend fun search(query: String): List<AnimeSource> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("$HOST/search.html?keyword=$query")
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            doc.select(".last_episodes > ul > li div.img > a").map { el: Element ->
                AnimeSource(
                    slug = el.attr("href").replace("/category/", ""),
                    name = el.attr("title"),
                    cover = el.select("img").attr("src"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getEpisodes(slug: String): List<Episode> = withContext(Dispatchers.IO) {
        val episodes = mutableListOf<Episode>()
        try {
            val doc = Jsoup.connect("$HOST/category/$slug")
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            // New anitaku.to structure
            val episodeLinks = doc.select("ul#episode_related a[href], ul.ep-range a[href]")
            if (episodeLinks.isNotEmpty()) {
                for (el in episodeLinks.reversed()) {
                    val href = el.attr("href").trim()
                    if (!href.contains("-episode-")) continue
                    val num = el.attr("data-num").ifEmpty {
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
                    val ajax = Jsoup.connect(
                        "https://ajax.gogocdn.net/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId"
                    )
                        .userAgent(USER_AGENT)
                        .timeout(10000)
                        .get()

                    for (el in ajax.select("ul > li > a").reversed()) {
                        val num = el.select(".name").text().replace("EP", "").trim()
                        episodes.add(Episode(number = num, link = HOST + el.attr("href").trim()))
                    }
                }
            }
        } catch (e: Exception) {
            // silent fail
        }
        episodes
    }

    suspend fun getStreamLinks(episodeLink: String): List<StreamLink> = withContext(Dispatchers.IO) {
        val links = mutableListOf<StreamLink>()
        try {
            val doc = Jsoup.connect(episodeLink)
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

            Log.d("GogoParser", "── getStreamLinks: ${servers.size} servers found ──")
            for ((name, url, subs) in servers) {
                Log.d("GogoParser", "  Server: $name | embed: $url | subs: ${subs.size}")
                subs.forEach { s -> Log.d("GogoParser", "    Sub: ${s.label} → ${s.url}") }
                val extracted = extractDirectLink(name, url, subs)
                if (extracted != null) {
                    Log.d("GogoParser", "  ✓ Extracted: ${extracted.url} | subs: ${extracted.subtitles.size}")
                    links.add(extracted)
                } else {
                    Log.d("GogoParser", "  ✗ Failed to extract direct link")
                }
            }
            Log.d("GogoParser", "── Total stream links: ${links.size} ──")
        } catch (e: Exception) {
            Log.e("GogoParser", "getStreamLinks failed", e)
        }
        links
    }

    private fun extractDirectLink(name: String, url: String, subtitles: List<SubtitleTrack> = emptyList()): StreamLink? {
        return try {
            val page = Jsoup.connect(url)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .header("Referer", "$HOST/")
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get().html()

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
            val unpacked = unpackJsPacked(page)
            if (unpacked != null) {
                // Also try to extract subtitle tracks from unpacked JWPlayer config
                val allSubs = try {
                    subtitles.ifEmpty { parseSubtitlesFromUnpacked(unpacked) }
                } catch (_: Exception) { subtitles }

                val unpackedM3u8 = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""").find(unpacked)?.value
                if (unpackedM3u8 != null) {
                    return StreamLink(server = name, url = unpackedM3u8, quality = "Auto", headers = headers, subtitles = allSubs)
                }
                val unpackedMp4 = Regex("""(https?://[^\s"'\\]+\.mp4[^\s"'\\]*)""").find(unpacked)?.value
                if (unpackedMp4 != null) {
                    return StreamLink(server = name, url = unpackedMp4, quality = "Auto", headers = headers, subtitles = allSubs)
                }
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Unpacks Dean Edwards' p.a.c.k.e.d JavaScript — the obfuscation used by
     * most GogoAnime embed servers to hide stream URLs.
     */
    private fun unpackJsPacked(html: String): String? {
        // Match eval(function(p,a,c,k,e,d){...}('payload',base,count,'keys'.split('|')))
        val packed = Regex(
            """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)"""
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
            var i = 1
            while (true) {
                val captionUrl = uri.getQueryParameter("caption_$i") ?: break
                val label = uri.getQueryParameter("sub_$i") ?: "Track $i"
                tracks.add(SubtitleTrack(url = captionUrl, label = label))
                i++
            }
        } catch (_: Exception) {}
        return tracks
    }

    /**
     * Extracts subtitle tracks from unpacked JWPlayer config.
     * Looks for VTT URLs in the JS source.
     */
    private fun parseSubtitlesFromUnpacked(js: String): List<SubtitleTrack> {
        return try {
            val vttPattern = Regex("""(https?://[^\s"'\\]+\.vtt[^\s"'\\]*)""")
            vttPattern.findAll(js).map { match ->
                SubtitleTrack(url = match.value, label = "English")
            }.distinctBy { it.url }.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun httpsIfy(text: String): String =
        if (text.startsWith("//")) "https:$text" else text
}
