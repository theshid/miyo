package ani.saikou.domain.usecase.downloads

import ani.saikou.domain.model.DownloadsSnapshot
import ani.saikou.domain.model.MangaWithDownloads
import ani.saikou.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Stream the downloads-screen snapshot. Owns the join between the
 * manga library and the per-chapter download rows (filters out manga
 * with zero matching chapters — stale junk left by interrupted
 * delete-alls), and re-queries the storage + evictable metrics every
 * upstream tick so the cleanup banner stays in sync.
 */
class ObserveDownloadsUseCase(
    private val repository: DownloadRepository,
) {
    operator fun invoke(): Flow<DownloadsSnapshot> =
        combine(
            repository.observeAllDownloadedManga(),
            repository.observeAllDownloads(),
        ) { manga, downloads ->
            val grouped =
                manga
                    .map { m ->
                        MangaWithDownloads(
                            manga = m,
                            chapters = downloads.filter { it.mangaId == m.mangaId },
                        )
                    }.filter { it.chapters.isNotEmpty() }

            DownloadsSnapshot(
                mangaWithDownloads = grouped,
                totalStorageUsed = repository.storageUsedBytes(),
                freeSpace = repository.availableSpaceBytes(),
                readChapters = repository.listEvictableReadChapters(),
            )
        }
}
