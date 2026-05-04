package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.Download
import ani.saikou.domain.repository.DownloadRepository

/**
 * "Is this chapter fully downloaded?" — returns the completed [Download]
 * row or null. Reader uses this for the cache-first load path so the
 * offline experience doesn't hit the network.
 */
class GetCompletedChapterUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(
        mangaId: Int,
        chapterNumber: Int,
    ): Download? = repository.getCompletedChapter(mangaId, chapterNumber)
}
