package ani.saikou.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    /** Continue Watching: in-progress entries (not completed), most recent first. */
    @Query("SELECT * FROM watch_history WHERE (lastPositionMs * 1.0 / durationMs) < 0.9 ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getInProgress(limit: Int = 10): Flow<List<WatchHistoryEntity>>

    /** Watch history: all entries (completed + in progress) most recent first. */
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 10): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId")
    suspend fun getForMedia(mediaId: Int): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Int)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()
}
