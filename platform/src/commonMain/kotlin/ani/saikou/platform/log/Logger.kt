package ani.saikou.platform.log

/**
 * Cross-platform structured-error reporting hook. Concrete implementations
 * (e.g. SentryLogger on Android) live in platform-specific modules; commonMain
 * code only sees this interface so it stays Sentry-free and KMP-safe.
 *
 * Implementations must be best-effort — a failure inside the logger itself
 * should never propagate. Callers expect "fire and forget".
 */
interface Logger {
    /**
     * Capture a non-fatal exception with structured context. The [area] tag
     * groups events by subsystem (e.g. "MangaDexParser"), [method] narrows it
     * to a function, and [extras] carries operation-specific values (manga id,
     * query, chapter id, …) that are surfaced as searchable metadata.
     */
    fun reportError(
        area: String,
        method: String,
        throwable: Throwable,
        extras: Map<String, String> = emptyMap(),
    )

    /**
     * Capture a non-fatal warning — same shape as [reportError] but without
     * a throwable. Used for "this shouldn't happen" diagnostics where there's
     * no exception to attach (e.g. a backend that should always return data
     * silently returned an empty result).
     */
    fun reportWarning(
        area: String,
        method: String,
        message: String,
        extras: Map<String, String> = emptyMap(),
    )
}
