package ani.saikou.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC")
    fun getAll(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE mangaId = :mangaId")
    suspend fun getForManga(mangaId: Int): ReadingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE mangaId = :mangaId")
    suspend fun delete(mangaId: Int)

    @Query("DELETE FROM reading_history")
    suspend fun deleteAll()

    /**
     * Total chapters read: sum of the highest completed chapter across all manga.
     * Counts chapters that reached ≥80% progress (last page near the end).
     * Emits a new value whenever reading_history changes.
     */
    @Query("SELECT COALESCE(SUM(chapterNumber), 0) FROM reading_history WHERE totalPages > 0 AND ((lastPage + 1) * 1.0 / totalPages) >= 0.8")
    fun getChaptersReadCount(): Flow<Int>
}
