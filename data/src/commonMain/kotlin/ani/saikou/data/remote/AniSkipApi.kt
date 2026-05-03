package ani.saikou.data.remote

import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Client for the AniSkip community API — returns crowd-sourced opening/ending
 * timestamps keyed by MAL ID + episode number.
 *
 * Pure Ktor + serialization-json — lives in commonMain. The [HttpClient] is
 * supplied via DI so the engine choice (OkHttp on Android, Darwin/CIO on
 * future iOS) stays out of this file.
 *
 * Docs: https://api.aniskip.com/api-docs
 */
class AniSkipApi(
    private val client: HttpClient,
    private val logger: Logger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches OP/ED skip intervals for the given MAL ID and episode.
     * Returns a [SkipTimes] with nullable start/end pairs (seconds).
     * On any failure, returns [SkipTimes.EMPTY] — never throws.
     */
    suspend fun getSkipTimes(
        malId: Int,
        episodeNumber: Int,
    ): SkipTimes {
        return try {
            val response = client.get(AniSkipSite.Endpoints.skipTimes(malId, episodeNumber))
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val found = body[AniSkipSite.JsonKeys.FOUND]?.jsonPrimitive?.content == "true"
            if (!found) return SkipTimes.EMPTY

            val results = body[AniSkipSite.JsonKeys.RESULTS]?.jsonArray ?: return SkipTimes.EMPTY

            var opStart: Float? = null
            var opEnd: Float? = null
            var edStart: Float? = null
            var edEnd: Float? = null

            // Continues are guard clauses on chained JSON nullability, not jumps.
            @Suppress("LoopWithTooManyJumpStatements")
            for (result in results) {
                val obj = result.jsonObject
                val type = obj[AniSkipSite.JsonKeys.SKIP_TYPE]?.jsonPrimitive?.content ?: continue
                val interval = obj[AniSkipSite.JsonKeys.INTERVAL]?.takeIf { it != JsonNull }?.jsonObject ?: continue
                val start = interval[AniSkipSite.JsonKeys.START_TIME]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue
                val end = interval[AniSkipSite.JsonKeys.END_TIME]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue

                when (type) {
                    AniSkipSite.SkipTypes.OP -> {
                        opStart = start
                        opEnd = end
                    }
                    AniSkipSite.SkipTypes.ED -> {
                        edStart = start
                        edEnd = end
                    }
                }
            }

            SkipTimes(opStartSec = opStart, opEndSec = opEnd, edStartSec = edStart, edEndSec = edEnd)
        } catch (e: Exception) {
            logger.reportError(
                area = "AniSkipApi",
                method = "getSkipTimes",
                throwable = e,
                extras = mapOf("malId" to malId.toString(), "episode" to episodeNumber.toString()),
            )
            SkipTimes.EMPTY
        }
    }
}

/**
 * AniSkip API contract — endpoint composition, JSON keys, and skip-type tokens.
 *
 * Grouped here so an API change is a one-place fix rather than scattered
 * magic strings the catch path would swallow silently.
 */
internal object AniSkipSite {
    private const val HOST = "https://api.aniskip.com"

    object Endpoints {
        private const val SKIP_TIMES_BASE = "$HOST/v2/skip-times"
        private const val QUERY = "?types=op&types=ed&episodeLength=0"

        fun skipTimes(
            malId: Int,
            episodeNumber: Int,
        ): String = "$SKIP_TIMES_BASE/$malId/$episodeNumber$QUERY"
    }

    object JsonKeys {
        const val FOUND = "found"
        const val RESULTS = "results"
        const val SKIP_TYPE = "skipType"
        const val INTERVAL = "interval"
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
    }

    object SkipTypes {
        const val OP = "op"
        const val ED = "ed"
    }
}

data class SkipTimes(
    val opStartSec: Float? = null,
    val opEndSec: Float? = null,
    val edStartSec: Float? = null,
    val edEndSec: Float? = null,
) {
    companion object {
        val EMPTY = SkipTimes()
    }
}
