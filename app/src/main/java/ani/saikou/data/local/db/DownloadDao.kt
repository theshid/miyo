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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE downloads SET downloadedPages = :pages, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, pages: Int, status: String)

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
