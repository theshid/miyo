package ani.saikou.data.source.manga

/**
 * Single source of truth for the mangapill.com scraping contract:
 * endpoint paths, CSS selectors that hit the site's current HTML, and
 * regex patterns for URL parsing. Centralized so a site-side markup
 * change can be repaired in one place — the catch path that swallowed
 * a parsing error and returned `emptyList()` won't tell you which
 * selector went stale; the inventory here will.
 */
internal object MangaPillSite {
    const val HOST = "https://mangapill.com"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /** CDN rejects image requests without a Referer matching the site. */
    val REFERER_HEADERS = mapOf("Referer" to "$HOST/")

    object Paths {
        fun search(query: String): String = "$HOST/search?q=$query"

        fun absoluteUrl(pathOrUrl: String): String = if (pathOrUrl.startsWith("http")) pathOrUrl else "$HOST$pathOrUrl"
    }

    object Selectors {
        /** Anchor wrapping every search hit — matched against [Patterns.MANGA_HREF]
         *  to reject genre-browse links that share the prefix. */
        const val SEARCH_RESULT_LINK = "a[href^=/manga/]"
        const val SEARCH_TITLE = "div.font-black"
        const val SEARCH_IMG = "img"
        const val CHAPTER_LINKS = "a[href*=/chapters/]"

        /** CDN-served chapter pages — `data-src` for lazy load, `src` as fallback. */
        const val PAGE_IMAGES = "img[data-src*=mangap], img[data-src*=cdn]"
    }

    object Patterns {
        /** Canonical `/manga/{id}/{slug}` — guards against genre/listing pages. */
        val MANGA_HREF = Regex("""/manga/\d+/.*""")

        /** Captures e.g. "chapter-12.5" from a chapter URL into group 1. */
        val CHAPTER_NUMBER = Regex("""chapter-(\d+(?:\.\d+)?)""")
    }

    /** "Ch. 5" — matches the MangaDex side's chapter-name format. */
    fun chapterName(number: String): String = "Ch. $number"
}
