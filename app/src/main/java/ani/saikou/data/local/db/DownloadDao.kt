package ani.saikou.data.local.db

import androidx.room.Dao
import androidx.room.Delete
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
    suspend fun getDownloadByChapter(mangaId: Int, chapterKey: String): DownloadEntity?

    /**
     * Look up a completed download by chapter NUMBER — not by parser-specific
     * chapter id. Lets the reader find a downloaded chapter without a network
     * round-trip to the source. Picks the most recently created if multiple
     * sources have downloaded the same chapter.
     */
    @Query("""
        SELECT * FROM downloads
        WHERE mangaId = :mangaId AND chapterNumber = :chapterNumber AND status = 'COMPLETED'
        ORDER BY createdAt DESC LIMIT 1
    """)
    suspend fun getCompletedByChapterNumber(mangaId: Int, chapterNumber: Int): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE downloads SET downloadedPages = :pages, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, pages: Int, status: String)

    @Query("UPDATE downloads SET fileSizeBytes = :bytes WHERE id = :id")
    suspend fun updateFileSize(id: String, bytes: Long)

    /** Average completed-chapter size for a specific manga — best signal if available. */
    @Query("""
        SELECT AVG(fileSizeBytes) FROM downloads
        WHERE mangaId = :mangaId AND status = 'COMPLETED' AND fileSizeBytes > 0
    """)
    suspend fun getAverageSizeForManga(mangaId: Int): Double?

    /** Fallback: average completed-chapter size across every manga the user has downloaded. */
    @Query("""
        SELECT AVG(fileSizeBytes) FROM downloads
        WHERE status = 'COMPLETED' AND fileSizeBytes > 0
    """)
    suspend fun getGlobalAverageSize(): Double?

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("DELETE FROM downloads WHERE mangaId = :mangaId")
    suspend fun deleteAllForManga(mangaId: Int)

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
