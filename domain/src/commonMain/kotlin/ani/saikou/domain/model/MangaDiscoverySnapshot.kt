package ani.saikou.domain.model

/**
 * One-shot result of the manga discovery screen's three parallel reads.
 * [failure] is set only when the underlying transport reported an error
 * AND all three sections came back empty — see
 * [AnimeDiscoverySnapshot] for the same correlation rationale.
 */
data class MangaDiscoverySnapshot(
    val trending: List<Media>,
    val recentlyUpdated: List<Media>,
    val popular: List<Media>,
    val failure: AnilistFailure? = null,
)
