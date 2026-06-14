package ani.saikou.domain.source

/**
 * Issues Cloudflare clearance bundles that anime/manga scrapers can use to
 * bypass the "Just a moment…" challenge.
 *
 * Implementations are expected to be host-scoped and cache-aware — a single
 * solve costs a WebView round-trip (potentially user interaction), so the
 * cache should honor Cloudflare's own expiry on `cf_clearance` and refuse
 * to thrash. A short circuit breaker on consecutive failures is also
 * expected so repeated scraper attempts during an outage don't relaunch the
 * WebView on every miss.
 *
 * Lives in `:domain` so providers and use cases can take the interface
 * without depending on Android — the Android-only WebView impl lives in
 * `:data-android`.
 */
interface CloudflareClearanceProvider {
    /**
     * Returns fresh clearance for [host]. May suspend while the WebView
     * solves a challenge. Returns null when:
     *
     * - The challenge requires interactive user action (Managed Challenge)
     *   and the headless solver couldn't complete it.
     * - The circuit breaker has tripped for this host (recent failures).
     * - The platform layer can't host a WebView (e.g. tests).
     *
     * Callers SHOULD treat null as `AnimeSourceFailure.Blocked` and surface
     * accordingly. The cache decides whether the call hits a live WebView or
     * returns a cached bundle.
     */
    suspend fun getClearance(host: String): CloudflareClearance?

    /**
     * Drop any cached clearance for [host]. Call when a request using a
     * previously valid clearance still returned a 403 / challenge — means
     * Cloudflare rotated the cookie out from under us and the next attempt
     * needs a fresh solve. Suspending so the impl can take its cache mutex
     * without blocking a caller thread.
     */
    suspend fun invalidate(host: String)
}
