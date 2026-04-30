package ani.saikou.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityEventDao {
    @Insert
    suspend fun insert(event: ActivityEventEntity)

    @Query("SELECT * FROM activity_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
    fun getSince(sinceMs: Long): Flow<List<ActivityEventEntity>>

    @Query("SELECT COUNT(*) FROM activity_events WHERE type = :type AND timestampMs >= :sinceMs")
    suspend fun countByTypeSince(
        type: String,
        sinceMs: Long,
    ): Int

    @Query("DELETE FROM activity_events WHERE timestampMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
