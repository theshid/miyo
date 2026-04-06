package ani.saikou.data.remote.parsers

import ani.saikou.domain.model.AnimeSource
import ani.saikou.domain.model.Episode
import ani.saikou.domain.model.StreamLink
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

            val servers = mutableListOf<Pair<String, String>>()

            // New structure
            for (el in doc.select("li.server a.server-video[data-video]")) {
                val videoUrl = el.attr("data-video")
                val serverName = el.text().replace("Choose this server", "").trim()
                if (videoUrl.isNotEmpty() && serverName.isNotEmpty()) {
                    servers.add(serverName to httpsIfy(videoUrl))
                }
            }

            // Fallback
            if (servers.isEmpty()) {
                for (el in doc.select("div.anime_muti_link > ul > li:not(li.anime) a[data-video]")) {
                    val videoUrl = el.attr("data-video")
                    val serverName = el.text().replace("Choose this server", "").trim()
                    if (videoUrl.isNotEmpty() && serverName.isNotEmpty()) {
                        servers.add(serverName to httpsIfy(videoUrl))
                    }
                }
            }

            for ((name, url) in servers) {
                val extracted = extractDirectLink(name, url)
                if (extracted != null) links.add(extracted)
            }
        } catch (e: Exception) {
            // silent fail
        }
        links
    }

    private fun extractDirectLink(name: String, url: String): StreamLink? {
        return try {
            val page = Jsoup.connect(url)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .header("Referer", "$HOST/")
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get().html()

            val m3u8 = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""").find(page)?.value
            if (m3u8 != null) {
                return StreamLink(server = name, url = m3u8, quality = "Auto", headers = mapOf("Referer" to url))
            }

            val mp4 = Regex("""(https?://[^\s"'\\]+\.mp4[^\s"'\\]*)""").find(page)?.value
            if (mp4 != null) {
                return StreamLink(server = name, url = mp4, quality = "Auto", headers = mapOf("Referer" to url))
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpsIfy(text: String): String =
        if (text.startsWith("//")) "https:$text" else text
}
