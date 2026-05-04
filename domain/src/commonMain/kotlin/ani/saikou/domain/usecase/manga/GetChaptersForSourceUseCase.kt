package ani.saikou.domain.usecase.manga

import ani.saikou.domain.model.Chapter
import ani.saikou.domain.repository.MangaSourceRepository

/**
 * "What chapters does this source host for this manga?" — used by the
 * reader's chapter-list sheet and the next-chapter prefetch path.
 * Source-id shape decides which parser the repo dispatches to.
 */
class GetChaptersForSourceUseCase(
    private val repository: MangaSourceRepository,
) {
    suspend operator fun invoke(sourceMangaId: String): List<Chapter> = repository.getChapters(sourceMangaId)
}
