package ani.saikou.data.source.anime

/**
 * Identifies a Cloudflare "Just a moment..." challenge response so the
 * parser can surface a typed failure instead of trying to extract content
 * from a JS-challenge page.
 *
 * Detection follows Cloudflare's official guidance, layered by reliability:
 * 1. `cf-mitigated: challenge` response header (introduced for app/CDN
 *    callers — Cloudflare's documented machine-readable signal).
 * 2. HTTP status in [403, 429, 503] AND body contains a challenge tell:
 *    the literal title string, the challenge SDK domain, or one of the
 *    classic browser-verification markers.
 *
 * The body sniff stays restricted to known status codes so unrelated 200
 * pages that mention "Cloudflare" don't false-positive.
 */
internal object CloudflareDetector {
    private const val CHALLENGE_TITLE_FRAGMENT = "Just a moment..."
    private const val TURNSTILE_DOMAIN = "challenges.cloudflare.com"
    private const val LEGACY_BROWSER_VERIFICATION = "cf-browser-verification"
    private const val INTERSTITIAL_CLASS = "cf-im-under-attack"
    private val CHALLENGE_STATUS_CODES = setOf(403, 429, 503)

    /**
     * @param statusCode HTTP status from the response.
     * @param headersLookup case-insensitive header accessor returning the
     *                      first value for the given name (or null).
     * @param body raw response body — `null` is treated as "not a challenge"
     *             so callers can short-circuit on header alone.
     */
    fun isChallenge(
        statusCode: Int,
        headersLookup: (String) -> String?,
        body: String?,
    ): Boolean {
        if (headersLookup("cf-mitigated")?.contains("challenge", ignoreCase = true) == true) return true
        if (statusCode !in CHALLENGE_STATUS_CODES || body == null) return false
        return body.contains(CHALLENGE_TITLE_FRAGMENT, ignoreCase = true) ||
            body.contains(TURNSTILE_DOMAIN, ignoreCase = true) ||
            body.contains(LEGACY_BROWSER_VERIFICATION, ignoreCase = true) ||
            body.contains(INTERSTITIAL_CLASS, ignoreCase = true)
    }

    /** Returns the `cf-ray` header value, when present. Useful for log cross-correlation. */
    fun cfRay(headersLookup: (String) -> String?): String? = headersLookup("cf-ray")
}
