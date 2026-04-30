package ani.saikou.domain.source

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.MangaSearchResult

/**
 * A read-only catalog of manga (MangaDex, MangaPill, …). Implementations live
 * in :data — domain only declares the surface so use cases and repositories
 * can compose multiple sources without taking a hard dep on any one parser.
 *
 * The id formats are source-specific and treated as opaque strings by callers:
 * MangaDex returns a UUID, MangaPill returns the manga URL path. A
 * `MangaSearchResult.id` from `search()` is the input expected by `getChapters()`,
 * and a `Chapter.id` is the input expected by `getPages()`.
 */
interface MangaSource {
    /** Best-effort search; should swallow network errors and return an empty list. */
    suspend fun search(query: String): List<MangaSearchResult>

    /** Full chapter list for the given manga, ordered ascending by chapter number. */
    suspend fun getChapters(sourceId: String): List<Chapter>

    /** Page-by-page image URLs (and any required headers, e.g. CDN Referer). */
    suspend fun getPages(chapterId: String): List<MangaPage>
}
