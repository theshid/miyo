package ani.saikou.data.android.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import ani.saikou.domain.source.CloudflareClearance
import ani.saikou.domain.source.CloudflareClearanceProvider
import ani.saikou.platform.log.Logger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * Off-screen WebView solver for Cloudflare's "Just a moment…" challenge.
 *
 * Per-host cache + concurrency guard + simple circuit breaker. A single
 * inflight solve per host is enforced via a mutex; cached bundles are
 * served until they expire (default 50 minutes — Cloudflare's `cf_clearance`
 * usually lives 60 min, we refresh slightly early to avoid races).
 *
 * If [`headlessTimeoutMs`] elapses without a `cf_clearance` cookie, the
 * solve fails. Three consecutive failures within [`CIRCUIT_WINDOW_MS`]
 * trip the breaker for [`CIRCUIT_COOLDOWN_MS`], during which every
 * `getClearance` call short-circuits to null — the parser layer surfaces
 * `Blocked` instead of relaunching the WebView on every retry.
 *
 * The WebView's User-Agent is read AFTER the challenge clears and bundled
 * with the cookies. Cookies are paired with the EXACT UA they were issued
 * for — using a hard-coded UA with a WebView-derived `cf_clearance` fails
 * the next request silently (Cloudflare correlates cookie + UA fingerprint).
 */
class WebViewClearanceProvider(
    private val appContext: Context,
    private val logger: Logger,
    private val headlessTimeoutMs: Long = DEFAULT_HEADLESS_TIMEOUT_MS,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
) : CloudflareClearanceProvider {
    private data class CachedClearance(
        val clearance: CloudflareClearance,
    )

    private data class CircuitState(
        val consecutiveFailures: Int,
        val firstFailureAt: Long,
        val openedUntil: Long,
    )

    private val cache = mutableMapOf<String, CachedClearance>()
    private val cacheMutex = Mutex()

    // Per-host single-flight mutex so two concurrent scrapes don't race on
    // launching parallel WebViews — the second waits and re-reads cache.
    private val perHostMutexes = mutableMapOf<String, Mutex>()
    private val perHostMutexesGuard = Mutex()

    private val circuit = mutableMapOf<String, CircuitState>()
    private val circuitMutex = Mutex()

    override suspend fun getClearance(host: String): CloudflareClearance? {
        val normalized = host.lowercase().removePrefix("www.")

        cacheMutex.withLock {
            cache[normalized]?.let { cached ->
                if (cached.clearance.expiresAtEpochMillis > System.currentTimeMillis()) {
                    return cached.clearance
                }
                cache.remove(normalized)
            }
        }

        if (isCircuitOpen(normalized)) {
            logger.reportWarning(
                area = AREA,
                method = "getClearance",
                message = "circuit breaker open — short-circuiting",
                extras = mapOf("host" to normalized),
            )
            return null
        }

        val mutex = mutexFor(normalized)
        mutex.withLock {
            // Re-check cache under the per-host lock — another caller might
            // have just solved the challenge while we waited.
            cacheMutex.withLock {
                cache[normalized]?.let { cached ->
                    if (cached.clearance.expiresAtEpochMillis > System.currentTimeMillis()) {
                        return cached.clearance
                    }
                }
            }

            val solved = solveOnce(normalized)
            if (solved == null) {
                recordFailure(normalized)
                return null
            }
            cacheMutex.withLock {
                cache[normalized] = CachedClearance(solved)
            }
            resetCircuit(normalized)
            return solved
        }
    }

    override suspend fun invalidate(host: String) {
        val normalized = host.lowercase().removePrefix("www.")
        cacheMutex.withLock { cache.remove(normalized) }
    }

    private suspend fun mutexFor(host: String): Mutex =
        perHostMutexesGuard.withLock {
            perHostMutexes.getOrPut(host) { Mutex() }
        }

    private suspend fun solveOnce(host: String): CloudflareClearance? =
        withContext(Dispatchers.Main) {
            val target = "https://$host/"
            val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
            // `removeSessionCookies` only drops cookies without an Expires/Max-Age —
            // `cf_clearance` is persistent, so a stale-but-rejected cookie would
            // survive and immediately register as "solved" on the next attempt,
            // re-issuing a fresh 50-min cache entry around the bad value. Clear
            // EVERY cookie so the new solve starts from a known-empty jar.
            suspendCancellableCoroutine<Unit> { cont ->
                cookieManager.removeAllCookies { cont.resume(Unit) }
            }
            cookieManager.flush()

            val webView = createSolverWebView()
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            try {
                val solved =
                    withTimeoutOrNull(headlessTimeoutMs) {
                        suspendCancellableCoroutine<CloudflareClearance?> { continuation ->
                            val latch = ContinuationLatch(continuation)
                            webView.webViewClient =
                                ClearanceWebViewClient(
                                    host = host,
                                    cookieManager = cookieManager,
                                    webView = webView,
                                    cacheTtlMs = cacheTtlMs,
                                    latch = latch,
                                )
                            webView.loadUrl(target)

                            // Backup poll — some CF flows leave the WebView idle on the
                            // real page after the challenge cleared without firing a
                            // distinguishable navigation event. The poll picks up the
                            // cookie even if the client callbacks don't.
                            val pollRunnable =
                                pollForCookie(
                                    host = host,
                                    webView = webView,
                                    cookieManager = cookieManager,
                                    cacheTtlMs = cacheTtlMs,
                                    latch = latch,
                                )

                            // Timeout (or any upstream cancel) must tear down the
                            // poll loop too — otherwise the runnable keeps re-posting
                            // and would touch the WebView after we destroy it in
                            // `finally`. `invokeOnCancellation` may fire on any
                            // thread, so hop back to the WebView's handler.
                            continuation.invokeOnCancellation {
                                webView.post {
                                    webView.removeCallbacks(pollRunnable)
                                }
                            }
                        }
                    }
                solved
            } finally {
                webView.stopLoading()
                webView.webViewClient = WebViewClient() // detach our callbacks
                webView.destroy()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createSolverWebView(): WebView {
        val webView = WebView(appContext)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        // Don't let the off-screen view paint anything visible.
        webView.visibility = android.view.View.INVISIBLE
        return webView
    }

    private fun pollForCookie(
        host: String,
        webView: WebView,
        cookieManager: CookieManager,
        cacheTtlMs: Long,
        latch: ContinuationLatch<CloudflareClearance?>,
    ): Runnable {
        val runnable =
            object : Runnable {
                override fun run() {
                    if (latch.isResumed) return
                    val cookiesHeader = cookieManager.getCookie("https://$host/")
                    if (cookiesHeader != null && cookiesHeader.contains("cf_clearance=")) {
                        val clearance = bundleClearance(host, cookiesHeader, webView, cacheTtlMs)
                        latch.tryResume(clearance)
                        return
                    }
                    webView.postDelayed(this, COOKIE_POLL_INTERVAL_MS)
                }
            }
        webView.post(runnable)
        return runnable
    }

    private fun bundleClearance(
        host: String,
        cookiesHeader: String,
        webView: WebView,
        cacheTtlMs: Long,
    ): CloudflareClearance {
        val cookies = parseCookieHeader(cookiesHeader)
        val ua = webView.settings.userAgentString ?: ""
        return CloudflareClearance(
            host = host,
            cookies = cookies,
            userAgent = ua,
            expiresAtEpochMillis = System.currentTimeMillis() + cacheTtlMs,
        )
    }

    private fun parseCookieHeader(header: String): Map<String, String> =
        header
            .split("; ")
            .mapNotNull { kv ->
                val idx = kv.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                kv.substring(0, idx) to kv.substring(idx + 1)
            }.toMap()

    // ── Circuit breaker ──────────────────────────────────────────────────

    private suspend fun isCircuitOpen(host: String): Boolean =
        circuitMutex.withLock {
            val now = System.currentTimeMillis()
            val state = circuit[host] ?: return@withLock false
            if (state.openedUntil > now) return@withLock true
            // Cooldown elapsed — clear and let the next solve attempt run.
            if (state.openedUntil != 0L) circuit.remove(host)
            false
        }

    private suspend fun recordFailure(host: String) =
        circuitMutex.withLock {
            val now = System.currentTimeMillis()
            val current = circuit[host]
            val nextState =
                when {
                    current == null || (now - current.firstFailureAt) > CIRCUIT_WINDOW_MS ->
                        CircuitState(consecutiveFailures = 1, firstFailureAt = now, openedUntil = 0L)
                    current.consecutiveFailures + 1 >= CIRCUIT_FAILURE_THRESHOLD ->
                        CircuitState(
                            consecutiveFailures = current.consecutiveFailures + 1,
                            firstFailureAt = current.firstFailureAt,
                            openedUntil = now + CIRCUIT_COOLDOWN_MS,
                        )
                    else ->
                        current.copy(consecutiveFailures = current.consecutiveFailures + 1)
                }
            circuit[host] = nextState
            if (nextState.openedUntil != 0L) {
                logger.reportWarning(
                    area = AREA,
                    method = "recordFailure",
                    message = "circuit breaker tripped",
                    extras =
                        mapOf(
                            "host" to host,
                            "failures" to nextState.consecutiveFailures.toString(),
                            "cooldown_ms" to CIRCUIT_COOLDOWN_MS.toString(),
                        ),
                )
            }
        }

    private suspend fun resetCircuit(host: String) =
        circuitMutex.withLock {
            circuit.remove(host)
        }

    /**
     * Tiny single-resume wrapper so we can race the WebViewClient's
     * `onPageFinished` callback with the polling Runnable without ever
     * double-resuming the underlying continuation.
     */
    private class ContinuationLatch<T>(
        private val cont: CancellableContinuation<T>,
    ) {
        @Volatile
        var isResumed: Boolean = false
            private set

        @Synchronized
        fun tryResume(value: T) {
            if (isResumed) return
            isResumed = true
            try {
                if (cont.isActive) cont.resume(value)
            } catch (_: CancellationException) {
                // Continuation already cancelled by the outer withTimeout.
            } catch (_: IllegalStateException) {
                // Lost the race — already resumed by another path.
            }
        }
    }

    /** WebViewClient that reads cookies on every page-finish event. */
    private inner class ClearanceWebViewClient(
        private val host: String,
        private val cookieManager: CookieManager,
        private val webView: WebView,
        private val cacheTtlMs: Long,
        private val latch: ContinuationLatch<CloudflareClearance?>,
    ) : WebViewClient() {
        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            if (latch.isResumed) return
            val cookies = cookieManager.getCookie("https://$host/") ?: return
            if (!cookies.contains("cf_clearance=")) return
            latch.tryResume(bundleClearance(host, cookies, webView, cacheTtlMs))
        }
    }

    private companion object {
        private const val AREA = "Cloudflare"
        private const val DEFAULT_HEADLESS_TIMEOUT_MS = 25_000L
        private const val DEFAULT_CACHE_TTL_MS = 50L * 60L * 1000L // 50 minutes
        private const val COOKIE_POLL_INTERVAL_MS = 500L
        private const val CIRCUIT_FAILURE_THRESHOLD = 3
        private const val CIRCUIT_WINDOW_MS = 5L * 60L * 1000L // 5 minutes
        private const val CIRCUIT_COOLDOWN_MS = 10L * 60L * 1000L // 10 minutes

        // Suspend so we can use it from a non-suspend ContinuationLatch builder.
        @Suppress("unused")
        private suspend fun yieldOnce() = delay(1)
    }
}
