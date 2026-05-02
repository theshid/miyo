package ani.saikou.data.source.manga

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.source.MangaSource
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Fallback manga source for titles not available on MangaDex (e.g. licensed manga).
 * Parses mangapill.com — no JS rendering, no Cloudflare, clean HTML.
 *
 * Endpoint paths, CSS selectors, and regex patterns all live in [MangaPillSite]
 * so a site-side markup change is a one-place fix.
 */
class MangaPillParser(
    private val logger: Logger,
) : MangaSource {
    override suspend fun search(query: String): List<MangaSearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val doc =
                    Jsoup
                        .connect(MangaPillSite.Paths.search(query))
                        .userAgent(MangaPillSite.USER_AGENT)
                        .timeout(10000)
                        .get()

                doc
                    .select(MangaPillSite.Selectors.SEARCH_RESULT_LINK)
                    .mapNotNull { el ->
                        val href = el.attr("href")
                        if (!href.matches(MangaPillSite.Patterns.MANGA_HREF)) return@mapNotNull null
                        val title =
                            el.select(MangaPillSite.Selectors.SEARCH_TITLE).text().ifEmpty {
                                el.attr("title").ifEmpty { null }
                            } ?: return@mapNotNull null
                        val cover =
                            el.select(MangaPillSite.Selectors.SEARCH_IMG).attr("data-src").ifEmpty {
                                el.select(MangaPillSite.Selectors.SEARCH_IMG).attr("src")
                            }

                        MangaSearchResult(
                            id = href, // e.g. "/manga/4741/vinland-saga"
                            title = title,
                            coverUrl = cover.ifEmpty { null },
                        )
                    }.distinctBy { it.id }
            } catch (e: Exception) {
                reportParserIssue("search", e, mapOf("query" to query))
                emptyList()
            }
        }

    override suspend fun getChapters(sourceId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val mangaPath = sourceId
            try {
                val doc =
                    Jsoup
                        .connect(MangaPillSite.Paths.absoluteUrl(mangaPath))
                        .userAgent(MangaPillSite.USER_AGENT)
                        .timeout(10000)
                        .get()

                doc
                    .select(MangaPillSite.Selectors.CHAPTER_LINKS)
                    .mapNotNull { el ->
                        val href = el.attr("href")
                        // Extract chapter number from URL: /chapters/4741-10001000/vinland-saga-chapter-1
                        val numMatch = MangaPillSite.Patterns.CHAPTER_NUMBER.find(href) ?: return@mapNotNull null
                        val number = numMatch.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
                        Chapter(
                            id = href,
                            number = number,
                            name = MangaPillSite.chapterName(numMatch.groupValues[1]),
                        )
                    }.sortedBy { it.number }
            } catch (e: Exception) {
                reportParserIssue("getChapters", e, mapOf("mangaPath" to mangaPath))
                emptyList()
            }
        }

    override suspend fun getPages(chapterId: String): List<MangaPage> =
        withContext(Dispatchers.IO) {
            val chapterPath = chapterId
            try {
                val doc =
                    Jsoup
                        .connect(MangaPillSite.Paths.absoluteUrl(chapterPath))
                        .userAgent(MangaPillSite.USER_AGENT)
                        .timeout(15000)
                        .maxBodySize(0) // some chapters have many pages
                        .get()

                // CDN requires Referer header to serve images.
                doc.select(MangaPillSite.Selectors.PAGE_IMAGES).mapIndexed { index, img ->
                    val imageUrl = img.attr("data-src").ifEmpty { img.attr("src") }
                    MangaPage(index = index, imageUrl = imageUrl, headers = MangaPillSite.REFERER_HEADERS)
                }
            } catch (e: Exception) {
                reportParserIssue("getPages", e, mapOf("chapterPath" to chapterPath))
                emptyList()
            }
        }

    private fun reportParserIssue(
        method: String,
        throwable: Throwable,
        extras: Map<String, String> = emptyMap(),
    ) {
        logger.reportError(area = "MangaPillParser", method = method, throwable = throwable, extras = extras)
    }
}
