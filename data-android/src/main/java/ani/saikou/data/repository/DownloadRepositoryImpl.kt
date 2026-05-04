package ani.saikou.data.repository

import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.DownloadEntity
import ani.saikou.data.local.db.DownloadedMangaEntity
import ani.saikou.data.local.downloads.ChapterSizeEstimator
import ani.saikou.data.local.downloads.MangaDownloadManager
import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadRequest
import ani.saikou.domain.model.DownloadStatus
import ani.saikou.domain.model.DownloadedManga
import ani.saikou.domain.model.EvictionSummary
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adapts the Room DAO + filesystem orchestrator pair into the domain
 * [DownloadRepository] surface. All Room-typed `DownloadEntity` rows are
 * mapped to the domain [Download] at the boundary; nothing storage-shaped
 * leaves the impl.
 */
class DownloadRepositoryImpl(
    private val dao: DownloadDao,
    private val manager: MangaDownloadManager,
    private val sizeEstimator: ChapterSizeEstimator,
) : DownloadRepository {
    override fun observeDownloadsForManga(mangaId: Int): Flow<Map<Int, Download>> =
        dao.getDownloadsForManga(mangaId).map { rows ->
            // Legacy rows from a pre-v6 schema can carry chapterNumber == -1.
            // Filter them out — they're unreachable from chapter-number-based UI.
            rows
                .filter { it.chapterNumber >= 0 }
                .associate { it.chapterNumber to it.toDomain() }
        }

    override suspend fun getDownload(id: String): Download? = dao.getDownload(id)?.toDomain()

    override suspend fun getCompletedChapter(
        mangaId: Int,
        chapterNumber: Int,
    ): Download? = dao.getCompletedByChapterNumber(mangaId, chapterNumber)?.toDomain()

    override suspend fun getLocalPages(
        mangaId: Int,
        chapterKey: String,
    ): List<MangaPage> = manager.getLocalPages(mangaId, chapterKey).orEmpty()

    override suspend fun estimateBytesForNext(
        mangaId: Int,
        count: Int,
    ): Long = sizeEstimator.estimateBytes(mangaId, count)

    override suspend fun queueChapter(request: DownloadRequest) {
        // Manager owns the duplicate-detection — it short-circuits when a row
        // is already COMPLETED. Repos don't add their own logic on top.
        manager.queueDownload(
            mangaId = request.mangaId,
            mangaTitle = request.mangaTitle,
            coverUrl = request.coverUrl,
            chapterKey = request.chapterKey,
            chapterNumber = request.chapterNumber,
            chapterName = request.chapterName,
            sourceId = request.sourceId,
            totalPages = request.totalPages,
        )
    }

    override suspend fun cancelChapter(downloadId: String) {
        manager.cancelDownload(downloadId)
    }

    override suspend fun cancelChapterByNumber(
        mangaId: Int,
        chapterNumber: Int,
    ) {
        val row = dao.getByChapterNumber(mangaId, chapterNumber) ?: return
        manager.cancelDownload(row.id)
    }

    override suspend fun pauseChapter(downloadId: String) {
        manager.pauseDownload(downloadId)
    }

    override suspend fun deleteAllForManga(mangaId: Int) {
        manager.deleteAllForManga(mangaId)
    }

    override fun observeAllDownloads(): Flow<List<Download>> =
        dao.getAllDownloads().map { rows ->
            rows.map {
                it.toDomain()
            }
        }

    override fun observeAllDownloadedManga(): Flow<List<DownloadedManga>> =
        dao.getAllDownloadedManga().map { rows -> rows.map { it.toDomain() } }

    override suspend fun listEvictableReadChapters(): List<Download> =
        // Wrap in runCatching to mirror the DAO's pre-existing best-effort
        // contract — a malformed reading_history row shouldn't crash the
        // cleanup banner; an empty list is a safe degraded state.
        runCatching { dao.getReadCompletedDownloads() }
            .getOrDefault(emptyList())
            .map { it.toDomain() }

    override suspend fun evictReadChapters(): EvictionSummary {
        val targets = listEvictableReadChapters()
        var freed = 0L
        var removed = 0
        for (download in targets) {
            // cancelDownload removes both DB row + on-disk files in one pass.
            // We pre-tally bytesFreed because the row is gone after cancel().
            freed += download.fileSizeBytes
            manager.cancelDownload(download.id)
            removed++
        }
        return EvictionSummary(chaptersRemoved = removed, bytesFreed = freed)
    }

    override suspend fun storageUsedBytes(): Long = manager.getStorageUsed()

    override suspend fun availableSpaceBytes(): Long = manager.getAvailableSpace()

    private fun DownloadedMangaEntity.toDomain(): DownloadedManga =
        DownloadedManga(
            mangaId = mangaId,
            title = title,
            coverUrl = coverUrl,
            sourceId = sourceId,
        )

    private fun DownloadEntity.toDomain(): Download =
        Download(
            id = id,
            mangaId = mangaId,
            mangaTitle = mangaTitle,
            chapterKey = chapterKey,
            chapterNumber = chapterNumber,
            chapterName = chapterName,
            sourceId = sourceId,
            // Persisted strings can drift from the enum — fall back to ERROR
            // rather than null-propagate so the UI always has a state to render.
            status = DownloadStatus.fromString(status) ?: DownloadStatus.ERROR,
            totalPages = totalPages,
            downloadedPages = downloadedPages,
            fileSizeBytes = fileSizeBytes,
            createdAt = createdAt,
        )
}
