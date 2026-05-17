package ani.saikou.data.source.anime

import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Live-network smoke test that probes the GogoSite selectors against the real
 * anineko.to site. `@Ignore`d by default so CI never depends on upstream —
 * un-Ignore and run locally any time the site rebrands or stream extraction
 * stops working.
 *
 * Pass criteria: each Jsoup selector finds at least one element on a known
 * page. If any of these zero out, the corresponding step in GogoParser will
 * silently produce an empty list and the player will hang with no error
 * (this is exactly what happened during the anitaku → anineko migration:
 * the old server-link selector was `a.nv-server-btn.server-video[data-video]`
 * but the new site uses `<button>`, not `<a>`, so the selector matched 0
 * elements and `getStreamLinks()` returned an empty list).
 */
@Ignore("live-network probe; un-Ignore and run locally when verifying GogoSite selectors")
class GogoSelectorSmokeTest {
    @Test
    fun `search results selector matches at least one card`() {
        val doc =
            Jsoup
                .connect(GogoSite.Paths.search("naruto"))
                .userAgent(GogoSite.USER_AGENT)
                .timeout(10_000)
                .get()
        val results = doc.select(GogoSite.Selectors.SEARCH_RESULTS)
        assertTrue("search results selector returned 0 elements", results.size > 0)
    }

    @Test
    fun `episode list selector matches every episode of naruto-shippuden`() {
        val doc =
            Jsoup
                .connect(GogoSite.Paths.anime("naruto-shippuden"))
                .userAgent(GogoSite.USER_AGENT)
                .timeout(10_000)
                .get()
        val episodes = doc.select(GogoSite.Selectors.EPISODE_LINKS_PRIMARY)
        assertTrue("episode selector returned < 100 elements (expected ~500)", episodes.size > 100)
    }

    @Test
    fun `server-embed selector matches at least one embed`() {
        val doc =
            Jsoup
                .connect("${GogoSite.HOST}/watch/naruto-shippuden/ep-1")
                .userAgent(GogoSite.USER_AGENT)
                .timeout(10_000)
                .get()
        val servers = doc.select(GogoSite.Selectors.SERVER_LINKS_PRIMARY)
        assertTrue("server selector returned 0 elements", servers.size > 0)
    }
}
