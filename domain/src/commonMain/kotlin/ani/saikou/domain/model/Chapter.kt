package ani.saikou.domain.model

data class Chapter(
    val id: String,
    val number: Float,
    val name: String,
)

data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * One hit from a [MangaSource.search] call. Contains everything needed to
 * disambiguate which result the user (or auto-resolution logic) picks before
 * fetching chapters.
 */
data class MangaSearchResult(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    /**
     * Total chapter count the SOURCE believes the series has (from its own
     * metadata, e.g. MangaDex's `attributes.lastChapter`). Used to detect
     * licensed/partial listings where the source catalogs the title but only
     * hosts a handful of chapters. `null` when the source doesn't expose this.
     */
    val totalChapterHint: Int? = null,
)

/**
 * Pick the best result for [title] from a search response. Prefers an exact
 * (case-insensitive, whitespace-trimmed) title match; falls back to the
 * first result if no exact match exists.
 *
 * Why exact-title preference: source search-relevance ranking sometimes
 * floats colored re-releases or spin-offs above the canonical entry (the
 * canonical example: MangaDex returns "Vagabond (Hong Kong Colored Version)"
 * before "Vagabond"; the colored re-release only has 5 fragmentary chapters).
 * Without this preference, the source picker silently selects the wrong
 * series for downloads, page resolution, and chapter counts.
 */
fun List<MangaSearchResult>.pickBestMatch(title: String): MangaSearchResult? {
    val normalized = title.trim()
    return firstOrNull { it.title.trim().equals(normalized, ignoreCase = true) }
        ?: firstOrNull()
}
