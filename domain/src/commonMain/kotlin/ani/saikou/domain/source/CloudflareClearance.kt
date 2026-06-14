package ani.saikou.domain.source

/**
 * Cloudflare-issued credentials that let subsequent HTTP requests pass the
 * "Just a moment…" challenge. Issued by [`CloudflareClearanceProvider`]
 * after a WebView solves the challenge for a given host.
 *
 * The [userAgent] is non-optional and MUST be paired with [cookies] on
 * every request — Cloudflare correlates `cf_clearance` with the UA it was
 * issued for. Mixing a hardcoded UA with a WebView-derived cookie will
 * fail the challenge silently.
 *
 * Includes all cookies the WebView accumulated (not just `cf_clearance`),
 * because CF rotates auxiliary cookies (`__cf_bm`, `cf_chl_*`) that some
 * Workers/edge functions also check.
 */
data class CloudflareClearance(
    val host: String,
    val cookies: Map<String, String>,
    val userAgent: String,
    val expiresAtEpochMillis: Long,
)
