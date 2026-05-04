package ani.saikou.domain.usecase.manga

import ani.saikou.domain.model.ResolvedChapters
import ani.saikou.domain.repository.MangaSourceRepository

/**
 * "Which source has the most-complete chapter list for this title?" —
 * used by the reader's chapter-list sheet on first open. Returns null
 * when no source surfaces the title.
 */
class ResolveChaptersForMangaUseCase(
    private val repository: MangaSourceRepository,
) {
    suspend operator fun invoke(title: String): ResolvedChapters? = repository.resolveChapters(title)
}
