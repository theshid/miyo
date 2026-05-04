package ani.saikou.domain.repository

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSearchResult
import ani.saikou.domain.model.ResolvedChapters

/**
 * Aggregator over the configured [ani.saikou.domain.source.MangaSource]
 * implementations. Centralizes the "probe MangaDex first, fall back to
 * MangaPill" logic that screens used to copy-paste — exact-title
 * preference, partial-catalog detection, and AniList cross-check all
 * live here.
 *
 * All methods are best-effort; transient source failures are swallowed and
 * the next source is tried.
 */
interface MangaSourceRepository {
    /**
     * Manual search across configured sources. Used for the source-picker UI
     * on the detail screen. Results from multiple sources are returned in
     * one list; UI groups them as needed.
     */
    suspend fun search(query: String): List<MangaSearchResult>

    /**
     * Best-effort answer to "how many chapters does this manga have?".
     * Uses MangaDex's `lastChapter` hint, MangaDex's hosted chapter count,
     * and (when MangaDex looks partial or empty) MangaPill's count, and
     * picks the maximum. Returns null when neither source surfaces the
     * title.
     *
     * @param anilistTotal Optional — when set, used to detect cases where
     *   MangaDex's catalog is grossly behind AniList's count. Pass the
     *   `Media.totalChapters` for this title; pass null when unknown.
     */
    suspend fun resolveChapterCount(
        title: String,
        anilistTotal: Int? = null,
    ): Int?

    /**
     * Pick the most-complete source for [title] and return its chapter
     * list. Prefers MangaDex when its hosted count covers ≥90% of its own
     * `lastChapter` hint, otherwise falls through to MangaPill. Returns
     * null when no source surfaces the title.
     */
    suspend fun resolveChapters(title: String): ResolvedChapters?

    /**
     * Chapters hosted on the source for the given source-side manga id.
     * Impl picks the right parser by the id shape (MangaPill ids start
     * with `/manga/` or `/chapters/`; MangaDex ids are UUIDs), so callers
     * don't have to track which provider owned the lookup.
     */
    suspend fun getChapters(sourceMangaId: String): List<Chapter>

    /** Pages for a single chapter id; same auto-detect rule as [getChapters]. */
    suspend fun getPages(chapterId: String): List<MangaPage>
}
