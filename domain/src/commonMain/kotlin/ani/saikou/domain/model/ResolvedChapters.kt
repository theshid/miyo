package ani.saikou.domain.model

/**
 * The output of [ani.saikou.domain.repository.MangaSourceRepository.resolveChapters].
 * Carries enough context for the caller to (a) display the chapter list and
 * (b) come back later for pages from the same source — the [sourceMangaId]
 * is opaque to callers but stable across `getChapters` and `getPages` calls.
 */
data class ResolvedChapters(
    /** Human-readable source label (e.g. "MangaDex", "MangaPill"). */
    val sourceName: String,
    /** Source-specific manga identifier; pass to subsequent page fetches. */
    val sourceMangaId: String,
    val chapters: List<Chapter>,
)
