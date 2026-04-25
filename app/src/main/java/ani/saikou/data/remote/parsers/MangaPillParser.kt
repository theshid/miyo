package ani.saikou.data.remote.parsers

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSource
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Fallback manga source for titles not available on MangaDex (e.g. licensed manga).
 * Parses mangapill.com — no JS rendering, no Cloudflare, clean HTML.
 */
class MangaPillParser {

    companion object {
        private const val HOST = "https://mangapill.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    suspend fun search(query: String): List<MangaSource> = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect("$HOST/search?q=$query")
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            doc.select("a[href^=/manga/]").mapNotNull { el ->
                val href = el.attr("href")
                if (!href.matches(Regex("/manga/\\d+/.*"))) return@mapNotNull null
                val title = el.select("div.font-black").text().ifEmpty {
                    el.attr("title").ifEmpty { null }
                } ?: return@mapNotNull null
                val cover = el.select("img").attr("data-src").ifEmpty {
                    el.select("img").attr("src")
                }

                MangaSource(
                    id = href,  // e.g. "/manga/4741/vinland-saga"
                    title = title,
                    coverUrl = cover.ifEmpty { null },
                )
            }.distinctBy { it.id }
        } catch (e: Exception) {
            reportParserIssue("search", e, mapOf("query" to query))
            emptyList()
        }
    }

    suspend fun getChapters(mangaPath: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            val url = if (mangaPath.startsWith("http")) mangaPath else "$HOST$mangaPath"
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10000)
                .get()

            doc.select("a[href*=/chapters/]").mapNotNull { el ->
                val href = el.attr("href")
                // Extract chapter number from URL: /chapters/4741-10001000/vinland-saga-chapter-1
                val numMatch = Regex("""chapter-(\d+(?:\.\d+)?)""").find(href) ?: return@mapNotNull null
                val number = numMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                Chapter(
                    id = href,
                    number = number,
                    name = "Ch. ${numMatch.groupValues[1]}",
                )
            }.sortedBy { it.number }
        } catch (e: Exception) {
            reportParserIssue("getChapters", e, mapOf("mangaPath" to mangaPath))
            emptyList()
        }
    }

    suspend fun getPages(chapterPath: String): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            val url = if (chapterPath.startsWith("http")) chapterPath else "$HOST$chapterPath"
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .maxBodySize(0) // some chapters have many pages
                .get()

            // Select all images with CDN URLs (the manga page images)
            // CDN requires Referer header to serve images
            val referer = mapOf("Referer" to "$HOST/")
            doc.select("img[data-src*=mangap], img[data-src*=cdn]").mapIndexed { index, img ->
                val imageUrl = img.attr("data-src").ifEmpty { img.attr("src") }
                MangaPage(index = index, imageUrl = imageUrl, headers = referer)
            }
        } catch (e: Exception) {
            reportParserIssue("getPages", e, mapOf("chapterPath" to chapterPath))
            emptyList()
        }
    }

    private fun reportParserIssue(method: String, throwable: Throwable, extras: Map<String, String> = emptyMap()) {
        try {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setTag("area", "MangaPillParser")
                scope.setTag("method", method)
                extras.forEach { (k, v) -> scope.setExtra(k, v) }
                Sentry.captureException(throwable)
            }
        } catch (_: Exception) { /* best-effort */ }
    }
}
