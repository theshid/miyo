package ani.saikou.data.source.manga

import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the JSON-parsing paths in [MangaDexParser]. The HTTP client is wired
 * to a [MockEngine] that returns canned bodies routed by URL + query params,
 * so each test pins one specific contract:
 *
 *   - search: title locale fall-through, cover URL composition, the
 *     `lastChapter` → totalChapterHint adapter (incl. JsonNull guard);
 *   - getChapters: chapter-name format (with/without title), the EN→any
 *     language fallback, and the externalUrl skip;
 *   - getPages: the "prefer full data, fall back to dataSaver" branch and
 *     the URL composition that the reader downloads from.
 *
 * These are the load-bearing parsers — they sit between AniList and the
 * rest of the app, so a regression here would silently break everyone
 * who reads or downloads a chapter.
 */
class MangaDexParserTest {
    private val logger: Logger = mockk(relaxUnitFun = true)

    private fun parser(handler: MockRequestHandler) = MangaDexParser(HttpClient(MockEngine(handler)), logger)

    // -------- search() --------

    @Test
    fun `search parses EN title, cover URL, and lastChapter hint`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "data": [
                                {
                                  "id": "abc-123",
                                  "attributes": {
                                    "title": { "en": "Vagabond" },
                                    "lastChapter": "327"
                                  },
                                  "relationships": [
                                    { "id": "rel-1", "type": "cover_art", "attributes": { "fileName": "cover.jpg" } }
                                  ]
                                }
                              ]
                            }
                            """,
                    )
                }

            val out = parser.search("Vagabond")

            assertEquals(1, out.size)
            val hit = out[0]
            assertEquals("abc-123", hit.id)
            assertEquals("Vagabond", hit.title)
            assertEquals("https://uploads.mangadex.org/covers/abc-123/cover.jpg.256.jpg", hit.coverUrl)
            assertEquals(327, hit.totalChapterHint)
        }

    @Test
    fun `search falls through to first-available locale when no EN title`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "data": [
                                {
                                  "id": "abc",
                                  "attributes": { "title": { "ja-ro": "Bouken" }, "lastChapter": null },
                                  "relationships": []
                                }
                              ]
                            }
                            """,
                    )
                }

            val out = parser.search("anything")

            assertEquals("Bouken", out[0].title)
        }

    @Test
    fun `search emits null cover URL when relationships have no fileName`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "data": [
                                {
                                  "id": "abc",
                                  "attributes": { "title": { "en": "Title" } },
                                  "relationships": [ { "id": "rel-1", "type": "author" } ]
                                }
                              ]
                            }
                            """,
                    )
                }

            val out = parser.search("anything")

            assertNull(out[0].coverUrl)
        }

    @Test
    fun `search emits null totalChapterHint when lastChapter is JsonNull`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "data": [
                                {
                                  "id": "abc",
                                  "attributes": { "title": { "en": "Title" }, "lastChapter": null },
                                  "relationships": []
                                }
                              ]
                            }
                            """,
                    )
                }

            val out = parser.search("anything")

            assertNull(out[0].totalChapterHint)
        }

    @Test
    fun `search drops items that are missing the id field`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "data": [
                                { "attributes": { "title": { "en": "No ID" } }, "relationships": [] },
                                {
                                  "id": "valid-id",
                                  "attributes": { "title": { "en": "Has ID" } },
                                  "relationships": []
                                }
                              ]
                            }
                            """,
                    )
                }

            val out = parser.search("anything")

            assertEquals(1, out.size)
            assertEquals("valid-id", out[0].id)
        }

    @Test
    fun `search returns empty list when HTTP call fails`() =
        runTest {
            val parser = parser { _ -> respond("server error", HttpStatusCode.InternalServerError) }

            val out = parser.search("anything")

            assertTrue(out.isEmpty())
        }

    // -------- getChapters() --------

    @Test
    fun `getChapters formats name as 'Ch  N - Title' when title is present`() =
        runTest {
            val parser =
                parser { request ->
                    if (request.url.parameters["translatedLanguage[]"] == "en") {
                        respondJson(
                            body =
                                """
                                {
                                  "data": [
                                    {
                                      "id": "ch-uuid-1",
                                      "attributes": {
                                        "chapter": "5",
                                        "title": "Forest of Witches"
                                      }
                                    }
                                  ]
                                }
                                """,
                        )
                    } else {
                        respondJson(body = """{"data":[]}""")
                    }
                }

            val out = parser.getChapters("manga-id")

            assertEquals(1, out.size)
            assertEquals("ch-uuid-1", out[0].id)
            assertEquals(5f, out[0].number)
            assertEquals("Ch. 5 - Forest of Witches", out[0].name)
        }

    @Test
    fun `getChapters formats name as 'Ch  N' when title is JsonNull`() =
        runTest {
            val parser =
                parser { request ->
                    if (request.url.parameters["translatedLanguage[]"] == "en") {
                        respondJson(
                            body =
                                """
                                {
                                  "data": [
                                    {
                                      "id": "ch-uuid-2",
                                      "attributes": { "chapter": "10", "title": null }
                                    }
                                  ]
                                }
                                """,
                        )
                    } else {
                        respondJson(body = """{"data":[]}""")
                    }
                }

            val out = parser.getChapters("manga-id")

            assertEquals("Ch. 10", out[0].name)
        }

    @Test
    fun `getChapters falls back to any-language feed when EN feed is empty`() =
        runTest {
            val parser =
                parser { request ->
                    if (request.url.parameters["translatedLanguage[]"] == "en") {
                        respondJson(body = """{"data":[]}""")
                    } else {
                        // No translatedLanguage param — this is the fallback call.
                        respondJson(
                            body =
                                """
                                {
                                  "data": [
                                    {
                                      "id": "fallback-ch",
                                      "attributes": { "chapter": "1", "title": null }
                                    }
                                  ]
                                }
                                """,
                        )
                    }
                }

            val out = parser.getChapters("manga-id")

            assertEquals(1, out.size)
            assertEquals("fallback-ch", out[0].id)
        }

    @Test
    fun `getChapters skips externally-hosted chapters`() =
        runTest {
            val parser =
                parser { request ->
                    if (request.url.parameters["translatedLanguage[]"] == "en") {
                        respondJson(
                            body =
                                """
                                {
                                  "data": [
                                    {
                                      "id": "external",
                                      "attributes": {
                                        "chapter": "1",
                                        "title": "External",
                                        "externalUrl": "https://other-site.com/ch1"
                                      }
                                    },
                                    {
                                      "id": "hosted",
                                      "attributes": { "chapter": "2", "title": "Hosted" }
                                    }
                                  ]
                                }
                                """,
                        )
                    } else {
                        respondJson(body = """{"data":[]}""")
                    }
                }

            val out = parser.getChapters("manga-id")

            assertEquals(1, out.size)
            assertEquals("hosted", out[0].id)
        }

    @Test
    fun `getChapters drops entries with non-numeric chapter values`() =
        runTest {
            val parser =
                parser { request ->
                    if (request.url.parameters["translatedLanguage[]"] == "en") {
                        respondJson(
                            body =
                                """
                                {
                                  "data": [
                                    {
                                      "id": "oneshot",
                                      "attributes": { "chapter": "Oneshot", "title": null }
                                    },
                                    {
                                      "id": "valid",
                                      "attributes": { "chapter": "3", "title": null }
                                    }
                                  ]
                                }
                                """,
                        )
                    } else {
                        respondJson(body = """{"data":[]}""")
                    }
                }

            val out = parser.getChapters("manga-id")

            assertEquals(1, out.size)
            assertEquals("valid", out[0].id)
        }

    // -------- getPages() --------

    @Test
    fun `getPages prefers full data over dataSaver and builds correct URLs`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "baseUrl": "https://uploads.mangadex.org",
                              "chapter": {
                                "hash": "HASH",
                                "data": ["page1.jpg", "page2.jpg"],
                                "dataSaver": ["saver1.jpg"]
                              }
                            }
                            """,
                    )
                }

            val out = parser.getPages("chapter-id")

            assertEquals(2, out.size)
            assertEquals("https://uploads.mangadex.org/data/HASH/page1.jpg", out[0].imageUrl)
            assertEquals("https://uploads.mangadex.org/data/HASH/page2.jpg", out[1].imageUrl)
            assertEquals(0, out[0].index)
            assertEquals(1, out[1].index)
        }

    @Test
    fun `getPages falls back to dataSaver when full data is empty`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "baseUrl": "https://uploads.mangadex.org",
                              "chapter": {
                                "hash": "HASH",
                                "data": [],
                                "dataSaver": ["saver1.jpg"]
                              }
                            }
                            """,
                    )
                }

            val out = parser.getPages("chapter-id")

            assertEquals(1, out.size)
            assertEquals("https://uploads.mangadex.org/data-saver/HASH/saver1.jpg", out[0].imageUrl)
        }

    @Test
    fun `getPages returns empty list when chapter object is missing`() =
        runTest {
            val parser = parser { _ -> respondJson(body = """{ "baseUrl": "https://x" }""") }

            val out = parser.getPages("chapter-id")

            assertTrue(out.isEmpty())
        }

    @Test
    fun `getPages returns empty list when hash is missing`() =
        runTest {
            val parser =
                parser { _ ->
                    respondJson(
                        body =
                            """
                            {
                              "baseUrl": "https://x",
                              "chapter": { "data": ["page1.jpg"] }
                            }
                            """,
                    )
                }

            val out = parser.getPages("chapter-id")

            assertTrue(out.isEmpty())
        }

    // -------- helpers --------

    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = body.trimIndent(),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
}
