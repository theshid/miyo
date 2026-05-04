package ani.saikou.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    // ── Downloads ─────────────────────────────────────────────

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE mangaId = :mangaId ORDER BY chapterKey ASC")
    fun getDownloadsForManga(mangaId: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownload(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' OR status = 'DOWNLOADING' ORDER BY createdAt ASC")
    suspend fun getPendingDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE mangaId = :mangaId AND chapterKey = :chapterKey")
    suspend fun getDownloadByChapter(
        mangaId: Int,
        chapterKey: String,
    ): DownloadEntity?

    /**
     * Look up a completed download by chapter NUMBER — not by parser-specific
     * chapter id. Lets the reader find a downloaded chapter without a network
     * round-trip to the source. Picks the most recently created if multiple
     * sources have downloaded the same chapter.
     */
    @Query(
        """
        SELECT * FROM downloads
        WHERE mangaId = :mangaId AND chapterNumber = :chapterNumber AND status = 'COMPLETED'
        ORDER BY createdAt DESC LIMIT 1
    """,
    )
    suspend fun getCompletedByChapterNumber(
        mangaId: Int,
        chapterNumber: Int,
    ): DownloadEntity?

    /**
     * Find the row for a chapter NUMBER regardless of status. Used by the
     * picker's cancel/delete affordances, which receive a chapter number
     * but need the actual row id (which may be `mangaId_<UUID>` rather
     * than `mangaId_<chapterNumber>` after the `QueueNextChaptersUseCase`
     * fix started writing real source-side chapter ids as `chapterKey`).
     * Picks the most recently created when multiple rows match.
     */
    @Query(
        """
        SELECT * FROM downloads
        WHERE mangaId = :mangaId AND chapterNumber = :chapterNumber
        ORDER BY createdAt DESC LIMIT 1
        """,
    )
    suspend fun getByChapterNumber(
        mangaId: Int,
        chapterNumber: Int,
    ): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
    )

    /**
     * Move ERROR rows back into the queue for another shot, but only those that
     * haven't already been auto-retried [maxAttempts] times. Increments the
     * counter atomically so a stuck chapter eventually settles back in ERROR
     * instead of cycling forever. Returns how many rows were requeued.
     */
    @Query(
        """
        UPDATE downloads
        SET status = 'QUEUED', attemptCount = attemptCount + 1
        WHERE status = 'ERROR' AND attemptCount < :maxAttempts
        """,
    )
    suspend fun requeueRetryableErrors(maxAttempts: Int): Int

    @Query("UPDATE downloads SET downloadedPages = :pages, status = :status WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        pages: Int,
        status: String,
    )

    @Query("UPDATE downloads SET fileSizeBytes = :bytes WHERE id = :id")
    suspend fun updateFileSize(
        id: String,
        bytes: Long,
    )

    /** Average completed-chapter size for a specific manga — best signal if available. */
    @Query(
        """
        SELECT AVG(fileSizeBytes) FROM downloads
        WHERE mangaId = :mangaId AND status = 'COMPLETED' AND fileSizeBytes > 0
    """,
    )
    suspend fun getAverageSizeForManga(mangaId: Int): Double?

    /** Fallback: average completed-chapter size across every manga the user has downloaded. */
    @Query(
        """
        SELECT AVG(fileSizeBytes) FROM downloads
        WHERE status = 'COMPLETED' AND fileSizeBytes > 0
    """,
    )
    suspend fun getGlobalAverageSize(): Double?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    /**
     * Non-completed rows whose chapter number is strictly above the source's
     * known max — phantoms from the pre-fix `QueueNextChaptersUseCase` that
     * blindly queued `currentChapter + 1..N`. Excludes COMPLETED rows so we
     * never touch a chapter the user actually has on disk (could legitimately
     * have been downloaded from a source that hosts more chapters than the
     * current one). Returned for cancel-path teardown so any partial files
     * also get cleaned.
     */
    @Query(
        """
        SELECT * FROM downloads
        WHERE mangaId = :mangaId
          AND chapterNumber > :maxKnownChapter
          AND status != 'COMPLETED'
        """,
    )
    suspend fun getPhantomDownloads(
        mangaId: Int,
        maxKnownChapter: Int,
    ): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE mangaId = :mangaId")
    suspend fun deleteAllForManga(mangaId: Int)

    /**
     * Completed downloads where the user has read ≥80% of the chapter — the
     * "safe to evict" set for the manual cleanup action. The 80% threshold
     * matches what the home stat counts as "read" and lines up with the
     * AniList progress sync trigger.
     */
    @Query(
        """
        SELECT d.* FROM downloads d
        INNER JOIN reading_history h
          ON d.mangaId = h.mangaId AND d.chapterNumber = h.chapterNumber
        WHERE d.status = 'COMPLETED'
          AND d.chapterNumber > 0
          AND h.totalPages > 0
          AND ((h.lastPage + 1) * 1.0 / h.totalPages) >= 0.8
        """,
    )
    suspend fun getReadCompletedDownloads(): List<DownloadEntity>

    // ── Downloaded Manga ──────────────────────────────────────

    @Query("SELECT * FROM downloaded_manga ORDER BY title ASC")
    fun getAllDownloadedManga(): Flow<List<DownloadedMangaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedManga(manga: DownloadedMangaEntity)

    @Query("DELETE FROM downloaded_manga WHERE mangaId = :mangaId")
    suspend fun deleteDownloadedManga(mangaId: Int)

    // ── Stats ─────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE mangaId = :mangaId AND status = 'COMPLETED'")
    suspend fun getCompletedCountForManga(mangaId: Int): Int
}
