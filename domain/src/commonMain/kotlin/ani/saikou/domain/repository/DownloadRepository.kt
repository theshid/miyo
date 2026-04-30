package ani.saikou.domain.repository

import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadRequest
import ani.saikou.domain.model.DownloadedManga
import ani.saikou.domain.model.EvictionSummary
import ani.saikou.domain.model.MangaPage
import kotlinx.coroutines.flow.Flow

/**
 * Storage-agnostic interface over the chapter download queue. Implementations
 * adapt the underlying Room DAO + filesystem orchestrator into domain types,
 * so consumers (ViewModels, use cases) stay free of Room/Android imports.
 *
 * Surface scope: only the methods needed by `MangaReaderViewModel` today.
 * Other call sites (DownloadsViewModel, MediaDetailViewModel, the cleanup
 * banner) migrate in follow-up commits, which add to this interface as
 * needed.
 */
interface DownloadRepository {
    /**
     * Reactive map of `chapterNumber → Download` for [mangaId]. Emits the
     * current state immediately on collection, then updates on every DB
     * change. Filters out legacy rows with negative chapter numbers.
     */
    fun observeDownloadsForManga(mangaId: Int): Flow<Map<Int, Download>>

    /**
     * Single-shot lookup by row id (`"{mangaId}_{chapterNumber}"`). Used
     * before queueing to detect duplicates.
     */
    suspend fun getDownload(id: String): Download?

    /**
     * Find a previously-completed download by chapter NUMBER. Returns null
     * when the chapter isn't on disk. Used by the reader's offline path.
     */
    suspend fun getCompletedChapter(
        mangaId: Int,
        chapterNumber: Int,
    ): Download?

    /**
     * Materialize the on-disk pages for a previously-completed download
     * into the domain [MangaPage] shape (file:// URIs). Empty list when
     * files are missing or the download doesn't exist.
     */
    suspend fun getLocalPages(
        mangaId: Int,
        chapterKey: String,
    ): List<MangaPage>

    /**
     * Estimate bytes required to download [count] more chapters of [mangaId].
     * Backed by per-series and global running averages of completed downloads.
     */
    suspend fun estimateBytesForNext(
        mangaId: Int,
        count: Int,
    ): Long

    /**
     * Add a chapter to the queue. Idempotent — no-op if a row already exists
     * in QUEUED, DOWNLOADING, or COMPLETED status.
     */
    suspend fun queueChapter(request: DownloadRequest)

    /**
     * Cancel an in-flight or queued download. Removes any partial files and
     * the DB row.
     */
    suspend fun cancelChapter(downloadId: String)

    /** Pause an in-flight download — keeps partial files on disk so a
     *  subsequent resume picks up where it left off. */
    suspend fun pauseChapter(downloadId: String)

    /** Remove every download (and on-disk file) for [mangaId]. */
    suspend fun deleteAllForManga(mangaId: Int)

    // ─── Library-wide observations / queries ──────────────────────────────

    /** Reactive list of every download row across the whole DB. */
    fun observeAllDownloads(): Flow<List<Download>>

    /** Reactive list of every per-series row in `downloaded_manga`. */
    fun observeAllDownloadedManga(): Flow<List<DownloadedManga>>

    // ─── Cleanup / storage ────────────────────────────────────────────────

    /**
     * List downloads that are safe to evict — chapters the user has read
     * to ≥80% AND that are COMPLETED on disk. The threshold is enforced
     * at the storage layer; consumers just see the candidate list.
     */
    suspend fun listEvictableReadChapters(): List<Download>

    /**
     * Delete every download in [listEvictableReadChapters]. Returns a
     * summary the UI surfaces back to the user.
     */
    suspend fun evictReadChapters(): EvictionSummary

    /** Total bytes used by all downloads on disk. */
    suspend fun storageUsedBytes(): Long

    /** Bytes available on the filesystem hosting downloads. */
    suspend fun availableSpaceBytes(): Long
}
