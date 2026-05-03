package ani.saikou.domain.repository

import ani.saikou.domain.model.ReadingHistoryItem
import ani.saikou.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow

/**
 * The user's local watch + reading history. Backed by Room on the
 * [ani.saikou.data.repository.HistoryRepositoryImpl] side; the entity
 * types stay platform-resident, this interface speaks domain models only.
 */
interface HistoryRepository {
    fun observeRecentReading(limit: Int = 10): Flow<List<ReadingHistoryItem>>

    /** In-progress (not completed) anime, most-recently-watched first. */
    fun observeContinueWatching(limit: Int = 10): Flow<List<WatchHistoryItem>>

    fun observeRecentWatching(limit: Int = 10): Flow<List<WatchHistoryItem>>

    fun observeEpisodesWatchedCount(): Flow<Int>

    fun observeChaptersReadCount(): Flow<Int>
}
