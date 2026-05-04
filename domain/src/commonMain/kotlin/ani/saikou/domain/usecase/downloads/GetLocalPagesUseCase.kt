package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.repository.DownloadRepository

/**
 * "Read pages off disk for a downloaded chapter" — paired with
 * [GetCompletedChapterUseCase]. Returns empty when the chapter is queued
 * but not yet finished (caller falls back to the live source).
 */
class GetLocalPagesUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(
        mangaId: Int,
        chapterKey: String,
    ): List<MangaPage> = repository.getLocalPages(mangaId, chapterKey)
}
