package ani.saikou.domain.model

/**
 * One-shot result of the anime discovery screen's three parallel reads.
 *
 * [failure] is set only when (a) the underlying transport reported an
 * error after retries AND (b) all three sections came back empty —
 * AniList legitimately returns empty rows for some users (region/account
 * issues), so we don't surface a banner unless the network signal
 * confirms the empty result is unexpected.
 */
data class AnimeDiscoverySnapshot(
    val trending: List<Media>,
    val recentlyUpdated: List<Media>,
    val popular: List<Media>,
    val failure: AnilistFailure? = null,
)
