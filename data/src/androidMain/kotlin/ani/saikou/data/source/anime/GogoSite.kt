package ani.saikou.data.source.anime

/**
 * Single source of truth for the anitaku.to (a GogoAnime mirror) scraping
 * contract: endpoint paths, CSS selectors, regex patterns, and tokens the
 * markup stamps on entries. Centralized so a site-side markup change is a
 * one-place fix — and so the AJAX fallback URL on a separate CDN
 * (gogocdn.net) is documented next to the main paths it falls back from.
 */
internal object GogoSite {
    const val HOST = "https://anitaku.to"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /** Quality label exposed to the player when the source doesn't surface
     *  a real ladder. "Auto" lets the HLS adaptive logic in ExoPlayer pick. */
    const val DEFAULT_QUALITY = "Auto"

    object Paths {
        fun search(query: String): String = "$HOST/search.html?keyword=$query"

        fun anime(slug: String): String = "$HOST/category/$slug"

        /** AJAX endpoint that returns the full episode list — used when the
         *  category page only paginates them. Hosted on a separate CDN. */
        fun ajaxEpisodeList(
            animeId: String,
            lastEpisode: String,
        ): String = "https://ajax.gogocdn.net/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId"

        fun absoluteUrl(pathOrUrl: String): String = if (pathOrUrl.startsWith("http")) pathOrUrl else "$HOST$pathOrUrl"
    }

    object Selectors {
        const val SEARCH_RESULTS = ".last_episodes > ul > li div.img > a"
        const val EPISODE_LINKS_PRIMARY = "ul#episode_related a[href], ul.ep-range a[href]"
        const val EPISODE_NAME = ".name"

        /** Reads the last episode number off the paginator on the category page. */
        const val EPISODE_PAGE_LAST = "ul#episode_page > li:last-child > a"
        const val MOVIE_ID_INPUT = "input#movie_id"

        /** AJAX response wraps episodes in plain `<ul><li><a>`. */
        const val AJAX_EPISODE_ITEMS = "ul > li > a"

        /** Primary embed structure on anitaku.to. */
        const val SERVER_LINKS_PRIMARY = "li.server a.server-video[data-video]"

        /** Fallback for legacy gogoanime markup that some mirrors still ship. */
        const val SERVER_LINKS_FALLBACK = "div.anime_muti_link > ul > li:not(li.anime) a[data-video]"
    }

    object Tokens {
        /** Stripped from `data-num` / `.name` text to recover the bare number. */
        const val EPISODE_PREFIX = "EP"

        /** Trailing CTA on each server-link anchor; strip before using as a name. */
        const val SERVER_BUTTON_TEXT = "Choose this server"

        /** Path fragment used to derive a slug from a category URL. */
        const val CATEGORY_PREFIX = "/category/"

        const val EPISODE_PATH_FRAGMENT = "-episode-"
    }

    object Patterns {
        /** Captures the episode number from `…-episode-12` into group 1. */
        val EPISODE_NUMBER = Regex("""-episode-(\d+)""")

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
