package ani.saikou.data.source.anime

/**
 * Single source of truth for the anineko.to (the post-anitaku rebrand of
 * GogoAnime) scraping contract: endpoint paths, CSS selectors, regex
 * patterns, and tokens the markup stamps on entries. Centralized so a
 * site-side markup change is a one-place fix.
 *
 * anineko.to ships every episode of every series in a single fully-rendered
 * page, so there is no AJAX fallback for paginated listings (the legacy
 * gogocdn.net endpoint is dead anyway).
 */
internal object GogoSite {
    const val HOST = "https://anineko.to"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /** Quality label exposed to the player when the source doesn't surface
     *  a real ladder. "Auto" lets the HLS adaptive logic in ExoPlayer pick. */
    const val DEFAULT_QUALITY = "Auto"

    object Paths {
        fun search(query: String): String = "$HOST/browse?keyword=$query"

        fun anime(slug: String): String = "$HOST/watch/$slug"

        fun absoluteUrl(pathOrUrl: String): String = if (pathOrUrl.startsWith("http")) pathOrUrl else "$HOST$pathOrUrl"
    }

    object Selectors {
        /** Each search-result card on /browse. Selecting the thumb anchor
         *  gives one element per anime — the inner <img alt> carries the
         *  display name, and the anchor href carries the slug. */
        const val SEARCH_RESULTS = "article.nv-browse-card a.nv-anime-thumb"

        /** One anchor per episode on /watch/<slug>; href is /watch/<slug>/ep-N. */
        const val EPISODE_LINKS_PRIMARY = "a.nv-info-episode-main"

        /** Server-embed buttons on the episode page. `<button>` (NOT `<a>`)
         *  — the `data-video` attr carries the embed URL; the button text
         *  carries the server name (e.g. "HD-1 Hard Sub", "Earnvids Hard Sub"). */
        const val SERVER_LINKS_PRIMARY = "button[data-video]"
    }

    object Tokens {
        /** Trailing CTA on each server-link anchor; strip before using as a name. */
        const val SERVER_BUTTON_TEXT = "Choose this server"

        /** Path fragment used to derive a slug from an anime page URL. */
        const val CATEGORY_PREFIX = "/watch/"

        const val EPISODE_PATH_FRAGMENT = "/ep-"
    }

    object Patterns {
        /** Captures the episode number from `…/ep-12` into group 1. */
        val EPISODE_NUMBER = Regex("""/ep-(\d+)""")

        /** First m3u8 URL inside an HTML payload — used for both raw HTML
         *  and the unpacked p.a.c.k.e.d JS body. */
        val M3U8_URL = Regex("""(https?://[^\s"'\\]+\.m3u8[^\s"'\\]*)""")
        val MP4_URL = Regex("""(https?://[^\s"'\\]+\.mp4[^\s"'\\]*)""")
        val VTT_URL = Regex("""(https?://[^\s"'\\]+\.vtt[^\s"'\\]*)""")

        /** Dean Edwards' p.a.c.k.e.d JavaScript header. Captures: (1) payload,
         *  (2) base, (3) count, (4) keys table — used by the unpacker to
         *  reconstruct the original source. */
        val PACKED_JS =
            Regex(
                """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)""",
            )
    }
}
