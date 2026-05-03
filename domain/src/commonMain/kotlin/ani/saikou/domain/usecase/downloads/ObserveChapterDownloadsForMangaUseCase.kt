package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.Download
import ani.saikou.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

/**
 * Per-chapter download status keyed by chapter number, scoped to a
 * single manga. The detail screen consumes this to decorate each chapter
 * row (queued / downloading / completed).
 */
class ObserveChapterDownloadsForMangaUseCase(
    private val repository: DownloadRepository,
) {
    operator fun invoke(mangaId: Int): Flow<Map<Int, Download>> = repository.observeDownloadsForManga(mangaId)
}
