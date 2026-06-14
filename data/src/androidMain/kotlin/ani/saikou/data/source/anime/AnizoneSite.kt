package ani.saikou.data.source.anime

import java.net.URLEncoder

/**
 * Single source of truth for the anizone.to scraping contract. Centralized
 * so a markup change or CSRF/Livewire shape change is a one-place fix.
 *
 * anizone is a Laravel + Livewire + Alpine app:
 * - Search is a Livewire-hydrated component on `/anime?search=Q`. The
 *   initial GET returns shell HTML containing a CSRF token, a session
 *   cookie, and a `wire:snapshot` JSON blob. A POST to `/livewire/update`
 *   with that snapshot + matching cookie + CSRF returns the actual list.
 * - Detail and episode pages are SSR. Episodes list as
 *   `<a href="https://anizone.to/anime/{slug}/{N}">` in the DOM. Episode
 *   pages carry the HLS URL in `<source src="...m3u8">`.
 */
internal object AnizoneSite {
    const val HOST = "https://anizone.to"
    const val HOST_NAME = "anizone.to"

    // Anizone is browser-targeted; an Android-ish UA keeps the response shape
    // identical to what a phone WebView would see.
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Quality label exposed to the player — anizone serves HLS with adaptive variants. */
    const val DEFAULT_QUALITY = "Auto"

    object Paths {
        fun searchEntryPage(query: String): String = "$HOST/anime?search=${URLEncoder.encode(query, "UTF-8")}"

        fun livewireUpdate(): String = "$HOST/livewire/update"

        fun anime(slug: String): String = "$HOST/anime/$slug"
    }

    object Selectors {
        /** The third `wire:snapshot` on `/anime?search=Q` belongs to the search list
         *  component (the first two are the navbar and the mobile navbar). */
        const val WIRE_SNAPSHOT_ATTR = "wire:snapshot"
        const val CSRF_META = "meta[name=csrf-token]"

        /** Anchor on the SSR detail page (`/anime/{slug}`) pointing at an episode. */
        const val EPISODE_LINK_ON_DETAIL = """a[href^="https://anizone.to/anime/"][href*="/"]"""

        /** Video element on the episode page carrying the HLS / progressive source. */
        const val VIDEO_SOURCE_TAG = "source"
    }

    object Patterns {
        /** Matches an anizone anime-detail URL — `https://anizone.to/anime/{8-or-more-char-slug}` with no trailing path. */
        val ANIME_DETAIL_URL = Regex("""^https?://anizone\.to/anime/([A-Za-z0-9]{4,})$""")

        /** Matches an anizone episode URL — `…/anime/{slug}/{N}`. */
        val EPISODE_URL = Regex("""^https?://anizone\.to/anime/([A-Za-z0-9]{4,})/(\d+)$""")

        /** Pulls the JSON literal out of `anmTitles: JSON.parse('...')` inside an Alpine `x-data` attr. */
        val ANM_TITLES_JSON = Regex("""anmTitles:\s*JSON\.parse\('([^']+)'\)""")

        /** First m3u8 URL found in arbitrary HTML. */
        val M3U8_URL = Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")

        /** Backup: first mp4 URL. */
        val MP4_URL = Regex("""https?://[^\s"'\\]+\.mp4[^\s"'\\]*""")
    }
}
