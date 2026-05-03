package ani.saikou.data.remote

import ani.saikou.domain.model.AnilistFailure
import io.github.theshid.prettylog.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AnilistApi(
    private val tokenProvider: () -> String?,
) {
    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"
        private const val TAG = "AnilistApi"
        const val CLIENT_ID = 39345
        private const val MAX_ATTEMPTS = 3
    }

    private val _lastFailure = MutableStateFlow<AnilistFailure?>(null)

    /** Observed network/server errors. Cleared on the next successful execute. */
    val lastFailure: StateFlow<AnilistFailure?> = _lastFailure.asStateFlow()

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }

    suspend fun execute(
        query: String,
        variables: String = "",
    ): JsonObject? {
        val body =
            buildJsonObject {
                put("query", query)
                if (variables.isNotEmpty()) {
                    put("variables", json.parseToJsonElement(variables))
                }
            }.toString()

        var lastFailureForRetry: AnilistFailure? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt > 1) {
                // Exponential-ish backoff: 500ms, 1500ms.
                delay(500L * (1 shl (attempt - 2)))
                Log.d(TAG, "execute() retry attempt $attempt of $MAX_ATTEMPTS")
            }
            try {
                val response =
                    client.post(ENDPOINT) {
                        contentType(ContentType.Application.Json)
                        header("Accept", "application/json")
                        tokenProvider()?.let { token ->
                            header("Authorization", "Bearer $token")
                        }
                        setBody(body)
                    }
                val status = response.status.value

                // 5xx is transient — retry. 4xx is a client error (bad token,
                // malformed query) so retrying won't help.
                if (status in 500..599) {
                    lastFailureForRetry = AnilistFailure.Server(status)
                    Log.w(TAG, "HTTP $status from AniList — will retry")
                    continue
                }

                val responseText = response.bodyAsText()
                val jsonObj = json.decodeFromString<JsonObject>(responseText)

                // Surface GraphQL errors regardless of whether data is also present —
                // AniList happily returns 200 OK with { data: null, errors: [...] } for
                // malformed queries, variable type mismatches, auth failures, etc.
                val errors = jsonObj["errors"]?.takeIf { it != JsonNull }?.jsonArray
                if (!errors.isNullOrEmpty()) {
                    val summary =
                        errors.joinToString("; ") { err ->
                            err.jsonObject["message"]?.jsonPrimitive?.content ?: err.toString()
                        }
                    Log.w(TAG, "GraphQL errors (HTTP $status): $summary")
                    Log.w(TAG, "Failed query: ${query.take(200)}")
                    if (variables.isNotEmpty()) Log.w(TAG, "Variables: $variables")
                    reportApiIssue("AniList GraphQL errors", query, level = SentryLevel.WARNING) { scope ->
                        scope.setExtra("graphqlErrors", summary)
                        scope.setExtra("httpStatus", status.toString())
                    }
                }

                _lastFailure.value = null
                val data = jsonObj["data"]
                return if (data != null && data != JsonNull) jsonObj else null
            } catch (e: java.net.SocketTimeoutException) {
                lastFailureForRetry = AnilistFailure.Network(e)
                Log.w(TAG, "Socket timeout — will retry (${e.message})")
            } catch (e: java.io.IOException) {
                lastFailureForRetry = AnilistFailure.Network(e)
                Log.w(TAG, "IO error — will retry (${e.message})")
            } catch (e: Exception) {
                // Parse error, malformed JSON, etc — won't fix itself, bail now.
                Log.e(TAG, "execute() non-retriable failure: ${e.message}", e)
                Log.e(TAG, "Failed query: ${query.take(200)}")
                if (variables.isNotEmpty()) Log.e(TAG, "Variables: $variables")
                _lastFailure.value = AnilistFailure.Other(e)
                reportApiIssue(
                    message = "AniList request failed: ${e.javaClass.simpleName}",
                    query = query,
                    throwable = e,
                )
                return null
            }
        }

        // All retries exhausted on a transient failure.
        Log.e(TAG, "execute() failed after $MAX_ATTEMPTS attempts: $lastFailureForRetry")
        Log.e(TAG, "Failed query: ${query.take(200)}")
        _lastFailure.value = lastFailureForRetry
        when (lastFailureForRetry) {
            is AnilistFailure.Network ->
                reportApiIssue(
                    message = "AniList request failed after retries: ${lastFailureForRetry.cause.javaClass.simpleName}",
                    query = query,
                    throwable = lastFailureForRetry.cause,
                )
            is AnilistFailure.Server ->
                reportApiIssue(
                    message = "AniList HTTP ${lastFailureForRetry.httpStatus} after retries",
                    query = query,
                ) { scope -> scope.setExtra("httpStatus", lastFailureForRetry.httpStatus.toString()) }
            else -> { /* unreachable */ }
        }
        return null
    }

    /**
     * Centralized Sentry capture for AniList API failures so the dashboard
     * shows what kind of failure (timeout, GraphQL error, parse error) and
     * which query triggered it. Fails silently if Sentry isn't initialized.
     */
    private fun reportApiIssue(
        message: String,
        query: String,
        throwable: Throwable? = null,
        level: SentryLevel = SentryLevel.ERROR,
        extras: ((io.sentry.IScope) -> Unit)? = null,
    ) {
        try {
            Sentry.withScope { scope ->
                scope.level = level
                scope.setTag("area", "AnilistApi")
                scope.setTag("operation", extractOperationName(query))
                scope.setExtra("queryPreview", query.take(200))
                extras?.invoke(scope)
                if (throwable != null) {
                    Sentry.captureException(throwable)
                } else {
                    Sentry.captureMessage(message)
                }
            }
        } catch (_: Exception) {
            // best-effort
        }
    }

    /**
     * Best-effort extraction of the GraphQL operation name from a query string
     * like "query Trending(...) { ... }" → "Trending". Falls back to "unknown"
     * so the Sentry tag is always present (groupable on the dashboard).
     */
    private fun extractOperationName(query: String): String {
        val trimmed = query.trimStart()
        val keywordEnd =
            when {
                trimmed.startsWith("query") -> 5
                trimmed.startsWith("mutation") -> 8
                else -> return "unknown"
            }
        val rest = trimmed.substring(keywordEnd).trimStart()
        val nameEnd = rest.indexOfFirst { !it.isLetterOrDigit() && it != '_' }
        return if (nameEnd <= 0) "unknown" else rest.substring(0, nameEnd)
    }
}
