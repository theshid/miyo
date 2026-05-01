package ani.saikou.data.repository

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.source.MangaSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the source-priority logic in [MangaSourceRepositoryImpl]: MangaDex
 * is preferred; fall through to MangaPill when MangaDex is absent, partial
 * (hosted < 90% of its own `lastChapter` hint), or when AniList claims many
 * more chapters than MangaDex hosts. Each predicate captures a real past
 * failure mode — the Vagabond regression for AniList-far-above-Dex,
 * licensed-title cases for MangaDex-empty, partial-catalog gaps for the
 * coverage threshold.
 */
class MangaSourceRepositoryImplTest {
    private val mangaDex: MangaSource = mockk()
    private val mangaPill: MangaSource = mockk()
    private val repo = MangaSourceRepositoryImpl(mangaDex, mangaPill)

    private val title = "Vagabond"

    // -------- search() --------

    @Test
    fun `search concatenates dex then pill`() =
        runTest {
            coEvery { mangaDex.search(title) } returns listOf(searchHit("dex-1"))
            coEvery { mangaPill.search(title) } returns listOf(searchHit("pill-1"))

            val out = repo.search(title)

            assertEquals(listOf("dex-1", "pill-1"), out.map { it.id })
        }

    @Test
    fun `search swallows dex failure and returns pill results`() =
        runTest {
            coEvery { mangaDex.search(title) } throws RuntimeException("boom")
            coEvery { mangaPill.search(title) } returns listOf(searchHit("pill-1"))

            val out = repo.search(title)

            assertEquals(listOf("pill-1"), out.map { it.id })
        }

    @Test
    fun `search swallows pill failure and returns dex results`() =
        runTest {
            coEvery { mangaDex.search(title) } returns listOf(searchHit("dex-1"))
            coEvery { mangaPill.search(title) } throws RuntimeException("boom")

            val out = repo.search(title)

            assertEquals(listOf("dex-1"), out.map { it.id })
        }

    @Test
    fun `search returns empty when both sources fail`() =
        runTest {
            coEvery { mangaDex.search(title) } throws RuntimeException("boom")
            coEvery { mangaPill.search(title) } throws RuntimeException("boom")

            val out = repo.search(title)

            assertEquals(emptyList<MangaSearchResult>(), out)
        }

    // -------- resolveChapterCount() --------

    @Test
    fun `count returns dex when dex covers its hint and anilist agrees`() =
        runTest {
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 100)

            val out = repo.resolveChapterCount(title, anilistTotal = 100)

            assertEquals(100, out)
            coVerify(exactly = 0) { mangaPill.search(any()) }
        }

    @Test
    fun `count falls through to pill when dex returns no hits`() =
        runTest {
            coEvery { mangaDex.search(title) } returns emptyList()
            stubSource(mangaPill, id = "pill-id", hint = 50, chapterCount = 50)

            val out = repo.resolveChapterCount(title, anilistTotal = 50)

            assertEquals(50, out)
        }

    @Test
    fun `count falls through to pill when dex hosts a partial catalog`() =
        runTest {
            // 5 hosted out of a claimed 100 — partial catalog.
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 5)
            stubSource(mangaPill, id = "pill-id", hint = 100, chapterCount = 100)

            val out = repo.resolveChapterCount(title, anilistTotal = 100)

            assertEquals(100, out)
        }

    @Test
    fun `count falls through to pill when anilist is far above dex (Vagabond case)`() =
        runTest {
            // Dex catalogs only 5 chapters; AniList knows 327. The >2x rule
            // forces a pill probe even when dex's coverage looks fine on its
            // own (hint and hosted both 5).
            stubSource(mangaDex, id = "dex-id", hint = 5, chapterCount = 5)
            stubSource(mangaPill, id = "pill-id", hint = 327, chapterCount = 327)

            val out = repo.resolveChapterCount(title, anilistTotal = 327)

            assertEquals(327, out)
        }

    @Test
    fun `count probes pill when anilist total is null`() =
        runTest {
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 100)
            stubSource(mangaPill, id = "pill-id", hint = 100, chapterCount = 100)

            repo.resolveChapterCount(title, anilistTotal = null)

            coVerify(exactly = 1) { mangaPill.search(title) }
        }

    @Test
    fun `count returns the max of dex hosted, dex hint, and pill hosted`() =
        runTest {
            // Dex partial (5/200) → pill probed; pill hosts 50; dex hint 200.
            // The hint is the largest signal and should win.
            stubSource(mangaDex, id = "dex-id", hint = 200, chapterCount = 5)
            stubSource(mangaPill, id = "pill-id", hint = 50, chapterCount = 50)

            val out = repo.resolveChapterCount(title, anilistTotal = null)

            assertEquals(200, out)
        }

    @Test
    fun `count returns null when neither source surfaces the title`() =
        runTest {
            coEvery { mangaDex.search(title) } returns emptyList()
            coEvery { mangaPill.search(title) } returns emptyList()

            val out = repo.resolveChapterCount(title, anilistTotal = null)

            assertNull(out)
        }

    // -------- resolveChapters() --------

    @Test
    fun `chapters prefers dex when coverage meets threshold`() =
        runTest {
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 100)

            val out = repo.resolveChapters(title)

            assertEquals("MangaDex", out?.sourceName)
            assertEquals("dex-id", out?.sourceMangaId)
            assertEquals(100, out?.chapters?.size)
            coVerify(exactly = 0) { mangaPill.search(any()) }
        }

    @Test
    fun `chapters prefers dex when no hint is provided (coverage defaults to full)`() =
        runTest {
            stubSource(mangaDex, id = "dex-id", hint = null, chapterCount = 5)

            val out = repo.resolveChapters(title)

            assertEquals("MangaDex", out?.sourceName)
            assertEquals(5, out?.chapters?.size)
        }

    @Test
    fun `chapters falls through to pill when dex is partial`() =
        runTest {
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 5)
            stubSource(mangaPill, id = "pill-id", hint = 100, chapterCount = 100)

            val out = repo.resolveChapters(title)

            assertEquals("MangaPill", out?.sourceName)
            assertEquals("pill-id", out?.sourceMangaId)
            assertEquals(100, out?.chapters?.size)
            // Lock the id flow: pickBestMatch's pick must be the id forwarded
            // to getChapters. If the integration breaks (e.g. someone passes
            // the search query instead of the picked id), this fails.
            coVerify { mangaPill.getChapters("pill-id") }
        }

    @Test
    fun `chapters picks the canonical Vagabond id over the higher-ranked colored re-release`() =
        runTest {
            // Source returns the colored version first by relevance, but
            // pickBestMatch must prefer the exact-title canonical entry.
            // Pinned at the repo level so a regression in either pickBestMatch
            // or how the repo wires it surfaces here.
            coEvery { mangaDex.search(title) } returns
                listOf(
                    MangaSearchResult(
                        id = "colored",
                        title = "Vagabond (Hong Kong Colored Version)",
                        totalChapterHint = 5,
                    ),
                    MangaSearchResult(
                        id = "canonical",
                        title = title,
                        totalChapterHint = 327,
                    ),
                )
            coEvery { mangaDex.getChapters("canonical") } returns
                (1..327).map { Chapter(id = "canonical-ch-$it", number = it.toFloat(), name = "Chapter $it") }

            val out = repo.resolveChapters(title)

            assertEquals("MangaDex", out?.sourceName)
            assertEquals("canonical", out?.sourceMangaId)
            assertEquals(327, out?.chapters?.size)
            coVerify(exactly = 0) { mangaDex.getChapters("colored") }
        }

    @Test
    fun `chapters returns dex partial when pill yields nothing (best-effort fallback)`() =
        runTest {
            // Partial chapters beat returning null — readers can still open
            // what dex hosts even if the catalog looks short.
            stubSource(mangaDex, id = "dex-id", hint = 100, chapterCount = 5)
            coEvery { mangaPill.search(title) } returns emptyList()

            val out = repo.resolveChapters(title)

            assertEquals("MangaDex", out?.sourceName)
            assertEquals(5, out?.chapters?.size)
        }

    @Test
    fun `chapters returns pill when dex is absent`() =
        runTest {
            coEvery { mangaDex.search(title) } returns emptyList()
            stubSource(mangaPill, id = "pill-id", hint = 50, chapterCount = 50)

            val out = repo.resolveChapters(title)

            assertEquals("MangaPill", out?.sourceName)
            assertEquals(50, out?.chapters?.size)
        }

    @Test
    fun `chapters returns null when neither source surfaces the title`() =
        runTest {
            coEvery { mangaDex.search(title) } returns emptyList()
            coEvery { mangaPill.search(title) } returns emptyList()

            val out = repo.resolveChapters(title)

            assertNull(out)
        }

    // -------- helpers --------

    private fun searchHit(
        id: String,
        hint: Int? = null,
    ) = MangaSearchResult(id = id, title = title, totalChapterHint = hint)

    private fun stubSource(
        source: MangaSource,
        id: String,
        hint: Int?,
        chapterCount: Int,
    ) {
        coEvery { source.search(title) } returns listOf(searchHit(id, hint))
        val chapters =
            if (chapterCount == 0) {
                emptyList()
            } else {
                (1..chapterCount).map { i ->
                    Chapter(id = "$id-ch-$i", number = i.toFloat(), name = "Chapter $i")
                }
            }
        coEvery { source.getChapters(id) } returns chapters
    }
}
