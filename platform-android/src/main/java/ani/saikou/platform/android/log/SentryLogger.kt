package ani.saikou.platform.android.log

import ani.saikou.platform.log.Logger
import io.sentry.Sentry
import io.sentry.SentryLevel

/**
 * Android implementation of [Logger]. Routes structured errors into Sentry
 * as non-fatal captures with the provided area/method/extras as searchable
 * tags + extra fields.
 *
 * All Sentry interactions are wrapped in a try/catch — a logger failure
 * (Sentry not initialized, network down, …) must never bubble out and
 * disrupt the calling code. This mirrors the original parser behavior.
 */
class SentryLogger : Logger {
    override fun reportError(
        area: String,
        method: String,
        throwable: Throwable,
        extras: Map<String, String>,
    ) {
        try {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setTag("area", area)
                scope.setTag("method", method)
                extras.forEach { (k, v) -> scope.setExtra(k, v) }
                Sentry.captureException(throwable)
            }
        } catch (_: Exception) {
            // Best-effort — never fail the caller because logging failed.
        }
    }
}
