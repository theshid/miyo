package ani.saikou.data.remote

import io.github.theshid.prettylog.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
 * Docs: https://api.aniskip.com/api-docs
 */
class AniSkipApi {
    companion object {
        private const val BASE = "https://api.aniskip.com/v2/skip-times"
        private const val TAG = "AniSkipApi"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
        }

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
            Log.w(TAG, "Failed to fetch skip times for MAL $malId ep $episodeNumber: ${e.message}")
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
