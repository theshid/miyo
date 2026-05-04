package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.repository.DownloadRepository

/**
 * "How big will the next [count] chapters be?" — used by the reader's
 * "download next N" sheet to preview disk usage. Backed by per-series
 * measured averages with a global fallback.
 */
class EstimateNextChaptersBytesUseCase(
    private val repository: DownloadRepository,
) {
    suspend operator fun invoke(
        mangaId: Int,
        count: Int,
    ): Long = repository.estimateBytesForNext(mangaId, count)
}
