package ani.saikou.domain.model

/**
 * A single tracking event written each time the user finishes an episode
 * or a chapter. Aggregated per-day to back the home heatmap. Pre-v8 rows
 * may have null media context; consumers must treat the optional fields
 * as actually-optional.
 */
data class ActivityEvent(
    val timestampMs: Long,
    val type: String, // "watch" | "read"
    val mediaId: Int? = null,
    val mediaTitle: String? = null,
    val coverUrl: String? = null,
    val episodeNumber: Int? = null,
    val chapterNumber: Int? = null,
)
