package ani.saikou.logging

import ani.saikou.di.AppModule
import kotlin.system.measureTimeMillis

/**
 * Static-style logging facade. Delegates to the [LoggingService] registered
 * in [AppModule].
 *
 * Basic usage:
 *   Log.d(message = "User loaded")
 *   Log.d(tag = "Network", message = "Request sent")
 *   Log.e(tag = "Auth", message = "Token expired", throwable = ex)
 *   Log.wtf(message = "This should never happen")
 *
 * Network logging:
 *   Log.network(method = "POST", url = "https://graphql.anilist.co", status = 200, body = jsonResponse)
 *
 * Performance timing:
 *   val result = Log.timed("ParseEpisodes") { parser.getEpisodes(slug) }
 */
object Log {

    @PublishedApi
    internal val service: LoggingService get() = AppModule.loggingService()

    // ── Standard levels ──────────────────────────────────────

    fun d(tag: String? = null, message: String) =
        service.log(message = message, tag = tag, level = MonitoringLogLevel.Debug)

    fun i(tag: String? = null, message: String) =
        service.log(message = message, tag = tag, level = MonitoringLogLevel.Info)

    fun w(tag: String? = null, message: String) =
        service.log(message = message, tag = tag, level = MonitoringLogLevel.Warning)

    fun e(tag: String? = null, message: String, throwable: Throwable? = null) =
        service.log(message = message, tag = tag, level = MonitoringLogLevel.Error, error = throwable)

    fun wtf(tag: String? = null, message: String, throwable: Throwable? = null) =
        service.log(message = message, tag = tag, level = MonitoringLogLevel.Critical, error = throwable)

    // ── Network request/response logger ──────────────────────

    fun network(
        method: String,
        url: String,
        status: Int? = null,
        headers: Map<String, String>? = null,
        body: String? = null,
        durationMs: Long? = null,
        error: Throwable? = null,
    ) {
        val level = when {
            error != null -> MonitoringLogLevel.Error
            status != null && status >= 400 -> MonitoringLogLevel.Warning
            else -> MonitoringLogLevel.Debug
        }
        val message = buildString {
            append("$method $url")
            if (status != null) append("  →  $status")
            if (durationMs != null) append("  (${durationMs}ms)")
            if (headers != null && headers.isNotEmpty()) {
                appendLine()
                append("Headers:")
                headers.forEach { (k, v) -> appendLine(); append("  $k: $v") }
            }
            if (body != null) {
                appendLine()
                append("Body: $body")
            }
        }
        service.log(message = message, tag = "Network", level = level, error = error)
    }

    // ── Performance timing ───────────────────────────────────

    inline fun <T> timed(label: String, tag: String? = null, block: () -> T): T {
        var result: T
        val elapsed = measureTimeMillis { result = block() }
        service.log(
            message = "\u23F1 $label completed in ${elapsed}ms",
            tag = tag ?: "Perf",
            level = when {
                elapsed > 3000 -> MonitoringLogLevel.Warning
                elapsed > 1000 -> MonitoringLogLevel.Info
                else -> MonitoringLogLevel.Debug
            },
        )
        return result
    }
}
