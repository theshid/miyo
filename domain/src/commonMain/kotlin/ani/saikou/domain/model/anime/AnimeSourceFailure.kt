package ani.saikou.domain.model.anime

/**
 * Typed failure modes for the anime stream-source pipeline. Replaces the
 * earlier convention of swallowing every infrastructure error into an empty
 * list — the UI used to show "anime not found" / "no streams found" for
 * outages that had nothing to do with the requested title.
 *
 * Distinct from the legitimate-empty-result case (which is success carrying
 * an empty list).
 */
sealed class AnimeSourceFailure {
    /**
     * Source actively rejected us — Cloudflare challenge page, IP block, etc.
     * The site is up, just not for app traffic.
     */
    data class Blocked(
        /** HTTP status the source returned (403, 503, 429 are typical). */
        val statusCode: Int,
        /** Cloudflare ray ID, when present — useful for cross-correlation in logs. */
        val cfRay: String?,
    ) : AnimeSourceFailure()

    /**
     * Source returned a non-success status that doesn't look like CF blocking.
     * Could be a real outage or unexpected redirect chain.
     */
    data class Unavailable(
        val statusCode: Int,
    ) : AnimeSourceFailure()

    /**
     * Network never made it — DNS, connect timeout, TLS error, etc.
     */
    data class TransportError(
        val cause: Throwable,
    ) : AnimeSourceFailure()

    /**
     * Response arrived with a successful status but the page's HTML doesn't
     * match the selectors the parser expects. Strong signal that the source
     * changed its markup — the scraper contract needs updating.
     */
    data class ContractChanged(
        /** Which method tripped: search / getEpisodes / getStreamLinks. */
        val stage: String,
    ) : AnimeSourceFailure()
}
