package ani.saikou.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the exact-title preference. The Vagabond regression is the canonical
 * case: MangaDex returns "Vagabond (Hong Kong Colored Version)" first by
 * relevance, but that catalog hosts only 5 fragmentary chapters; the
 * canonical "Vagabond" entry has 327. Without pickBestMatch, downloads and
 * chapter-count probes silently picked the wrong series.
 */
class PickBestMatchTest {
    @Test
    fun `prefers exact title over higher-ranked fuzzy match`() {
        val results =
            listOf(
                result(id = "colored", title = "Vagabond (Hong Kong Colored Version)"),
                result(id = "canonical", title = "Vagabond"),
            )

        val picked = results.pickBestMatch("Vagabond")

        assertEquals("canonical", picked?.id)
    }

    @Test
    fun `match is case-insensitive`() {
        val results =
            listOf(
                result(id = "lowercase", title = "vagabond"),
            )

        val picked = results.pickBestMatch("VAGABOND")

        assertEquals("lowercase", picked?.id)
    }

    @Test
    fun `match trims surrounding whitespace`() {
        val results =
            listOf(
                result(id = "padded", title = "  Vinland Saga  "),
            )

        val picked = results.pickBestMatch("Vinland Saga")

        assertEquals("padded", picked?.id)
    }

    @Test
    fun `falls back to first result when no exact match exists`() {
        val results =
            listOf(
                result(id = "fuzzy-first", title = "Berserk: Lost Children Arc"),
                result(id = "fuzzy-second", title = "Berserk Side Stories"),
            )

        val picked = results.pickBestMatch("Berserk")

        assertEquals("fuzzy-first", picked?.id)
    }

    @Test
    fun `returns null when the result list is empty`() {
        val picked = emptyList<MangaSearchResult>().pickBestMatch("Any Title")

        assertNull(picked)
    }

    private fun result(
        id: String,
        title: String,
    ) = MangaSearchResult(id = id, title = title)
}
