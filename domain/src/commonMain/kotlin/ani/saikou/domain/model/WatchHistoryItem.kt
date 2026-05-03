package ani.saikou.domain.model

/**
 * One row in the user's anime watch history. One entry per anime — last
 * episode + playback position, plus the persistent source slug so resume
 * can skip the search lookup. Mirrors the data shape of the Room
 * `watch_history` table; the entity itself stays in :data-android.
 */
data class WatchHistoryItem(
    val mediaId: Int,
    val mediaTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val sourceSlug: String,
    val sourceName: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val completedEpisodes: Int,
    val lastWatchedAt: Long,
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isCompleted: Boolean
        get() = progressFraction >= 0.9f
}
