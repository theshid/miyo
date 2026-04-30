package ani.saikou.data.repository

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.model.ResolvedChapters
import ani.saikou.domain.repository.MangaSourceRepository
import ani.saikou.domain.source.MangaSource

/**
 * Default implementation. Wraps two [MangaSource] instances in priority
 * order — MangaDex first (richer catalog, better metadata), then MangaPill
 * for licensed/missing titles. Both are typed as the interface so tests
 * can substitute fakes without touching the concrete parser classes.
 *
 * The "should we fall through to MangaPill?" predicate captures three real
 * failure modes we've seen:
 *   - MangaDex doesn't index the title at all (licensed manga).
 *   - MangaDex catalogs it but only hosts a few chapters (the gap between
 *     `lastChapter` hint and hosted count exceeds 10%).
 *   - AniList claims many more chapters than MangaDex hosts (>2x), e.g.
 *     Vagabond — AniList knows 327, MangaDex hosts 5.
 */
class MangaSourceRepositoryImpl(
    private val mangaDex: MangaSource,
    private val mangaPill: MangaSource,
) : MangaSourceRepository {
    private companion object {
        const val SOURCE_MANGA_DEX = "MangaDex"
        const val SOURCE_MANGA_PILL = "MangaPill"

        /** MangaDex's hosted count must cover at least this fraction of its
         *  own `lastChapter` hint to count as "complete enough". */
        const val MANGADEX_COVERAGE_THRESHOLD = 0.9
    }

    override suspend fun search(query: String): List<MangaSearchResult> {
        // Concat from both sources — UI is responsible for grouping or
        // labeling. De-dup is intentionally NOT applied here: the same
        // title may exist on both sources with different ids, and the
        // user picks which one to use.
        val dex = runCatching { mangaDex.search(query) }.getOrDefault(emptyList())
        val pill = runCatching { mangaPill.search(query) }.getOrDefault(emptyList())
        return dex + pill
    }

    override suspend fun resolveChapterCount(
        title: String,
        anilistTotal: Int?,
    ): Int? {
        val dex = probe(mangaDex, title)
        val dexCount =
            dex
                ?.chapters
                ?.lastOrNull()
                ?.number
                ?.toInt() ?: 0
        val dexHint = dex?.hint ?: 0

        val mangaDexLooksPartial = dexHint > 0 && dexCount < (dexHint * MANGADEX_COVERAGE_THRESHOLD)
        val anilistFarAboveDex = anilistTotal != null && anilistTotal > dexCount * 2
        val anilistMissing = anilistTotal == null || anilistTotal == 0
        val needsPill = dexCount == 0 || mangaDexLooksPartial || anilistFarAboveDex || anilistMissing

        val pillCount =
            if (needsPill) {
                probe(mangaPill, title)
                    ?.chapters
                    ?.lastOrNull()
                    ?.number
                    ?.toInt() ?: 0
            } else {
                0
            }

        return listOf(dexCount, dexHint, pillCount).max().takeIf { it > 0 }
    }

    override suspend fun resolveChapters(title: String): ResolvedChapters? {
        val dex = probe(mangaDex, title)
        if (dex != null && dex.chapters.isNotEmpty()) {
            val coverage =
                if (dex.hint == null || dex.hint == 0) {
                    1.0
                } else {
                    dex.chapters.size.toDouble() / dex.hint
                }
            if (coverage >= MANGADEX_COVERAGE_THRESHOLD) {
                return ResolvedChapters(SOURCE_MANGA_DEX, dex.sourceMangaId, dex.chapters)
            }
        }

        // MangaDex absent or partial — try MangaPill.
        val pill = probe(mangaPill, title)
        if (pill != null && pill.chapters.isNotEmpty()) {
            return ResolvedChapters(SOURCE_MANGA_PILL, pill.sourceMangaId, pill.chapters)
        }

        // Final fallback: if MangaDex had *something* (even partial), prefer
        // it over returning null — partial chapters are better than nothing.
        if (dex != null && dex.chapters.isNotEmpty()) {
            return ResolvedChapters(SOURCE_MANGA_DEX, dex.sourceMangaId, dex.chapters)
        }
        return null
    }

    /**
     * Search [source] for [title], pick the best match (exact-title preferred
     * over relevance order — search-relevance sometimes ranks colored
     * re-releases above the canonical entry, e.g. Vagabond's Hong Kong
     * coloured version), then fetch its chapter list. Returns null when
     * the source has no hits.
     */
    private suspend fun probe(
        source: MangaSource,
        title: String,
    ): SourceProbe? {
        val results = runCatching { source.search(title) }.getOrDefault(emptyList())
        val picked =
            results.firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
                ?: results.firstOrNull()
                ?: return null
        val chapters = runCatching { source.getChapters(picked.id) }.getOrDefault(emptyList())
        return SourceProbe(picked.id, picked.totalChapterHint, chapters)
    }

    private data class SourceProbe(
        val sourceMangaId: String,
        val hint: Int?,
        val chapters: List<Chapter>,
    )
}
