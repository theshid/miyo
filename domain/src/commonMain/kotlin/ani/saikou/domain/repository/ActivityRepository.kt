package ani.saikou.domain.repository

import ani.saikou.domain.model.ActivityEvent
import kotlinx.coroutines.flow.Flow

/**
 * The user's local activity log — every watched episode + read chapter
 * leaves a row here. Backed by Room on the data side; this interface
 * exposes domain events only.
 */
interface ActivityRepository {
    /** All events on or after [sinceEpochMs], oldest first. */
    fun observeActivitySince(sinceEpochMs: Long): Flow<List<ActivityEvent>>

    /** Append one event row. Used by the player + reader on first progress tick. */
    suspend fun recordEvent(event: ActivityEvent)
}
