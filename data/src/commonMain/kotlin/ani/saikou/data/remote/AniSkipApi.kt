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
    companion object {
        private const val BASE = "https://api.aniskip.com/v2/skip-times"
    }

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
            val url = "$BASE/$malId/$episodeNumber?types=op&types=ed&episodeLength=0"
            val response = client.get(url)
            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject

            val found = body["found"]?.jsonPrimitive?.content == "true"
            if (!found) return SkipTimes.EMPTY

            val results = body["results"]?.jsonArray ?: return SkipTimes.EMPTY

            var opStart: Float? = null
            var opEnd: Float? = null
            var edStart: Float? = null
            var edEnd: Float? = null

            // Continues are guard clauses on chained JSON nullability, not jumps.
            @Suppress("LoopWithTooManyJumpStatements")
            for (result in results) {
                val obj = result.jsonObject
                val type = obj["skipType"]?.jsonPrimitive?.content ?: continue
                val interval = obj["interval"]?.takeIf { it != JsonNull }?.jsonObject ?: continue
                val start = interval["startTime"]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue
                val end = interval["endTime"]?.jsonPrimitive?.content?.toFloatOrNull() ?: continue

                when (type) {
                    "op" -> {
                        opStart = start
                        opEnd = end
                    }
                    "ed" -> {
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
