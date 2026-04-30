package ani.saikou.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks the user's watch history per anime.
 * One entry per anime — last watched episode + position is stored.
 * For full episode history, see WatchHistoryEventEntity.
 */
@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val mediaId: Int,
    val mediaTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val sourceSlug: String, // Gogo slug — skip search on resume
    val sourceName: String, // "Gogo"
    val lastPositionMs: Long, // playback position in millis
    val durationMs: Long, // total episode duration
    val completedEpisodes: Int = 0, // highest episode watched to ≥80% — only goes up
    val lastWatchedAt: Long = System.currentTimeMillis(),
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = progressFraction >= 0.9f
}
