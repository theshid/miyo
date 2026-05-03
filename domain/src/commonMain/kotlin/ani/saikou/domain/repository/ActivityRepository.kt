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
}
