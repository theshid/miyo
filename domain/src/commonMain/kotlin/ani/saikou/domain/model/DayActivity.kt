package ani.saikou.domain.model

/**
 * Something the user actually did on a given day — one watched episode
 * or one read chapter. The home heatmap groups these by day to show what
 * the user accomplished, distinct from the raw event count.
 */
data class DayActivity(
    val mediaId: Int,
    val title: String,
    val coverUrl: String?,
    val kind: Kind,
    val number: Int,
    val timestampMs: Long,
) {
    enum class Kind { WATCHED, READ }
}
