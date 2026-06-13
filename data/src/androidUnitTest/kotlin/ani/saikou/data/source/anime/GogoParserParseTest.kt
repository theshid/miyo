package ani.saikou.data.source.anime

import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.platform.log.Logger
import io.mockk.mockk
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture tests for the parser's pure parse-stage functions. Each method
 * takes an already-fetched [`Document`] so we can feed in hand-crafted HTML
 * and exercise success / contract-drift paths without mocking Jsoup.
 *
 * The fetch stage itself (CF detection, status classification, transport
 * errors) is covered by [`CloudflareDetectorTest`] in isolation.
 */
class GogoParserParseTest {
    private val logger: Logger = mockk(relaxUnitFun = true)
    private val parser = GogoParser(logger)

    @Test
    fun `parseSearchResults returns Success-emptyList for legitimate empty search`() {
        // A real anineko search page with zero matches still ships the page
        // chrome — the parser should treat zero matches as a legitimate
        // empty result (NOT contract-changed; an empty search for a typo'd
        // title is normal).
        val html =
            """
            <html><body>
              <header><h1>Browse</h1></header>
              <main>
                <p>No results for "kjasdhfkjs"</p>
              </main>
            </body></html>
            """.trimIndent()

        val result =
            parser.parseSearchResults(
                fetched(html, "https://anineko.to/browse?keyword=kjasdhfkjs"),
                mapOf("query" to "kjasdhfkjs"),
            )

        assertTrue(result is AnimeSourceResult.Success)
        assertEquals(0, (result as AnimeSourceResult.Success).value.size)
    }

    @Test
    fun `parseSearchResults extracts entries from real-shaped markup`() {
        val html =
            """
            <html><body>
              <article class="nv-browse-card">
                <a class="nv-anime-thumb" href="/watch/aoashi">
                  <img alt="Ao Ashi" src="https://cdn.anineko.to/img/aoashi.jpg" />
                </a>
              </article>
              <article class="nv-browse-card">
                <a class="nv-anime-thumb" href="/watch/aoashi-2">
                  <img alt="Ao Ashi 2" src="https://cdn.anineko.to/img/aoashi-2.jpg" />
                </a>
              </article>
            </body></html>
            """.trimIndent()

        val result = parser.parseSearchResults(fetched(html, "https://anineko.to/browse?keyword=aoashi"), mapOf("query" to "aoashi"))

        assertTrue(result is AnimeSourceResult.Success)
        val matches = (result as AnimeSourceResult.Success).value
        assertEquals(2, matches.size)
        assertEquals("aoashi", matches[0].slug)
        assertEquals("Ao Ashi", matches[0].name)
        assertEquals("https://cdn.anineko.to/img/aoashi.jpg", matches[0].cover)
    }

    @Test
    fun `parseEpisodes returns ContractChanged when episode selector matches nothing`() {
        // Page came back 200 OK with content but our selector finds no
        // episode anchors — classic markup-drift signal. The UI now shows
        // the dedicated "page format changed" message instead of mislabeling
        // it as "Episode not found".
        val html =
            """
            <html><body>
              <h1>Ao Ashi</h1>
              <p>Plot synopsis here</p>
            </body></html>
            """.trimIndent()

        val result = parser.parseEpisodes(fetched(html, "https://anineko.to/watch/aoashi"), mapOf("slug" to "aoashi"))

        assertTrue(result is AnimeSourceResult.Failed)
        val failure = (result as AnimeSourceResult.Failed).failure
        assertTrue(failure is AnimeSourceFailure.ContractChanged)
        assertEquals("getEpisodes", (failure as AnimeSourceFailure.ContractChanged).stage)
    }

    @Test
    fun `parseEpisodes returns Success with all matched episodes`() {
        val html =
            """
            <html><body>
              <a class="nv-info-episode-main" href="/watch/aoashi/ep-1">Ep 1</a>
              <a class="nv-info-episode-main" href="/watch/aoashi/ep-2">Ep 2</a>
              <a class="nv-info-episode-main" href="/watch/aoashi/ep-3">Ep 3</a>
            </body></html>
            """.trimIndent()

        val result = parser.parseEpisodes(fetched(html, "https://anineko.to/watch/aoashi"), mapOf("slug" to "aoashi"))

        assertTrue(result is AnimeSourceResult.Success)
        val episodes = (result as AnimeSourceResult.Success).value
        assertEquals(3, episodes.size)
        assertEquals("1", episodes[0].number)
        assertEquals("https://anineko.to/watch/aoashi/ep-1", episodes[0].link)
    }

    @Test
    fun `parseEpisodes returns ContractChanged when anchors match but href shape rejected`() {
        // Selector matched 5 anchors, but every href is missing the `/ep-N`
        // fragment — could be a navigation link reskin. Still contract-drift.
        val html =
            """
            <html><body>
              <a class="nv-info-episode-main" href="/some/other/path">Random</a>
              <a class="nv-info-episode-main" href="/another">Foo</a>
            </body></html>
            """.trimIndent()

        val result = parser.parseEpisodes(fetched(html, "https://anineko.to/watch/aoashi"), mapOf("slug" to "aoashi"))

        assertTrue(result is AnimeSourceResult.Failed)
        assertTrue((result as AnimeSourceResult.Failed).failure is AnimeSourceFailure.ContractChanged)
    }

    @Test
    fun `parseStreams returns ContractChanged when no server buttons present`() {
        val html =
            """
            <html><body>
              <h1>Ao Ashi - Episode 1</h1>
              <p>No embeds advertised here</p>
            </body></html>
            """.trimIndent()

        val result =
            parser.parseStreams(
                fetched(html, "https://anineko.to/watch/aoashi/ep-1"),
                mapOf("episodeLink" to "https://anineko.to/watch/aoashi/ep-1"),
            )

        assertTrue(result is AnimeSourceResult.Failed)
        val failure = (result as AnimeSourceResult.Failed).failure
        assertTrue(failure is AnimeSourceFailure.ContractChanged)
        assertEquals("getStreamLinks", (failure as AnimeSourceFailure.ContractChanged).stage)
    }

    @Test
    fun `parseStreams returns Success-emptyList when buttons exist but all embeds rejected`() {
        // Server button has empty data-video — collectServers skips it.
        // No embeds attempted, no failures, just zero links extracted.
        // This is NOT contract drift; the page shape is intact.
        val html =
            """
            <html><body>
              <button data-video="">Server 1 Choose this server</button>
            </body></html>
            """.trimIndent()

        val result =
            parser.parseStreams(
                fetched(html, "https://anineko.to/watch/aoashi/ep-1"),
                mapOf("episodeLink" to "https://anineko.to/watch/aoashi/ep-1"),
            )

        assertTrue(result is AnimeSourceResult.Success)
        assertEquals(0, (result as AnimeSourceResult.Success).value.size)
    }

    private fun fetched(
        html: String,
        url: String,
    ): GogoParser.FetchedResponse =
        GogoParser.FetchedResponse(
            document = Jsoup.parse(html, url),
            status = 200,
            host = "anineko.to",
            cfRay = null,
        )
}
