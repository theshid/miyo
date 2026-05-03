package ani.saikou.domain.usecase.anilist

import ani.saikou.domain.model.Media
import ani.saikou.domain.repository.AnilistRepository

/**
 * "Find media on AniList" — text query optionally narrowed by type, genre,
 * and sort. Backs the search screen's debounce-driven flow and the genre /
 * sort browse mode (no query, only filters). Pagination is the caller's
 * responsibility — the screen tracks its own page counter and concatenates.
 */
class SearchMediaUseCase(
    private val repository: AnilistRepository,
) {
    suspend operator fun invoke(
        query: String,
        type: String,
        genres: List<String>? = null,
        sort: String? = null,
        page: Int = 1,
    ): List<Media> = repository.search(query, type, genres, sort, page)
}
