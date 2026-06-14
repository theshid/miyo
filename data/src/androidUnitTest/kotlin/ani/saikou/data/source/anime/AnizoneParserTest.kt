package ani.saikou.data.source.anime

import ani.saikou.domain.model.anime.AnimeSourceFailure
import ani.saikou.domain.model.anime.AnimeSourceResult
import ani.saikou.platform.log.Logger
import io.mockk.mockk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture-driven tests for the Anizone Livewire response parser, focused on
 * the failure-mode classifier — that's where the typed-error promise lives.
 * The happy-path search extraction (parsing real anizone markup with
 * Alpine `x-data` blocks and `JSON.parse('…')` payloads) is validated by
 * live integration against the source rather than synthesized fixtures.
 */
class AnizoneParserTest {
    private val logger: Logger = mockk(relaxUnitFun = true)
    private val parser = AnizoneParser(logger)

    @Test
    fun `extractSearchResults returns Success-emptyList when Nothing found sentinel is present`() {
        // Anizone renders a literal "Nothing found." banner when the catalog
        // has zero matches for the query. Treating this as a legitimate
        // empty result keeps the UI honest — distinct from contract drift.
        val body = wrapInLivewireEnvelope("<div>Search...<div>Nothing found.</div></div>")

        val result = parser.extractSearchResults(body, mapOf("query" to "qqqqqqq"))

        assertTrue(result is AnimeSourceResult.Success)
        assertEquals(0, (result as AnimeSourceResult.Success).value.size)
    }

    @Test
    fun `extractSearchResults returns ContractChanged when JSON is unparsable`() {
        val body = "{ not actual json"

        val result = parser.extractSearchResults(body, mapOf("query" to "x"))

        assertTrue(result is AnimeSourceResult.Failed)
        val failure = (result as AnimeSourceResult.Failed).failure
        assertTrue(failure is AnimeSourceFailure.ContractChanged)
        assertEquals("search", (failure as AnimeSourceFailure.ContractChanged).stage)
    }

    @Test
    fun `extractSearchResults returns ContractChanged when effects-html is missing`() {
        val body =
            buildJsonObject {
                put(
                    "components",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("snapshot", JsonPrimitive("ok"))
                                put(
                                    "effects",
                                    buildJsonObject { put("returns", JsonArray(emptyList())) },
                                )
                            },
                        )
                    },
                )
            }.toString()

        val result = parser.extractSearchResults(body, mapOf("query" to "x"))

        assertTrue(result is AnimeSourceResult.Failed)
        assertNotNull((result as AnimeSourceResult.Failed).failure)
    }

    @Test
    fun `extractSearchResults returns ContractChanged when html lacks any anime anchors`() {
        // Page rendered something but didn't include any /anime/slug links and
        // didn't carry the "Nothing found" sentinel — strong markup-drift signal.
        val body = wrapInLivewireEnvelope("<div>Some unrelated chrome but no anime cards</div>")

        val result = parser.extractSearchResults(body, mapOf("query" to "x"))

        assertTrue(result is AnimeSourceResult.Failed)
        assertTrue((result as AnimeSourceResult.Failed).failure is AnimeSourceFailure.ContractChanged)
    }

    @Test
    fun `extractSearchResults skips cards with no recoverable title`() {
        // Anchor present and slug-shaped but no x-data anmTitles in the
        // surrounding markup — Alpine never wrote a real title. Drop these
        // rather than show a blank entry in the picker.
        val body =
            wrapInLivewireEnvelope(
                "<div><a href=\"http://anizone.to/anime/abcd1234\"><img src=\"https://x/a.jpg\"/></a></div>",
            )

        val result = parser.extractSearchResults(body, mapOf("query" to "x"))

        // No usable cards → contract change (we found anchors but couldn't
        // recover any titles from them).
        assertTrue(result is AnimeSourceResult.Failed)
        assertTrue((result as AnimeSourceResult.Failed).failure is AnimeSourceFailure.ContractChanged)
    }

    private fun wrapInLivewireEnvelope(rawHtml: String): String =
        buildJsonObject {
            put(
                "components",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("snapshot", JsonPrimitive("ok"))
                            put(
                                "effects",
                                buildJsonObject {
                                    put("returns", JsonArray(emptyList()))
                                    put("html", JsonPrimitive(rawHtml))
                                },
                            )
                        },
                    )
                },
            )
        }.toString()
}
