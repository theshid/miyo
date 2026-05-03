package ani.saikou.domain.model

/**
 * AniList side of the home screen's data: signed-in user, their
 * continue-watching / continue-reading lists, recommendations, and the
 * derived airing schedule (CURRENT-list shows with a future airing time,
 * sorted soonest first).
 *
 * [failure] is set only when *every* AniList read came back empty AND
 * the underlying transport reported a failure — same correlation rule
 * as the anime/manga discovery snapshots.
 */
data class HomeAnilistSnapshot(
    val user: User?,
    val continueWatching: List<Media>,
    val continueReading: List<Media>,
    val recommendations: List<Media>,
    val airingSchedule: List<Media>,
    val failure: AnilistFailure? = null,
)
