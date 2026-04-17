package ani.saikou.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AnilistApi(private val tokenProvider: () -> String?) {

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"
        private const val TAG = "AnilistApi"
        const val CLIENT_ID = 39345
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun execute(query: String, variables: String = ""): JsonObject? {
        return try {
            val body = buildJsonObject {
                put("query", query)
                if (variables.isNotEmpty()) {
                    put("variables", json.parseToJsonElement(variables))
                }
            }

            val response = client.post(ENDPOINT) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                tokenProvider()?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(body.toString())
            }
            val responseText = response.bodyAsText()
            val jsonObj = json.decodeFromString<JsonObject>(responseText)

            // Surface GraphQL errors regardless of whether data is also present —
            // AniList happily returns 200 OK with { data: null, errors: [...] } for
            // malformed queries, variable type mismatches, auth failures, etc. Without
            // this, mutation bugs silently no-op.
            val errors = jsonObj["errors"]?.takeIf { it != JsonNull }?.jsonArray
            if (!errors.isNullOrEmpty()) {
                val summary = errors.joinToString("; ") { err ->
                    err.jsonObject["message"]?.jsonPrimitive?.content ?: err.toString()
                }
                Log.w(TAG, "GraphQL errors (HTTP ${response.status.value}): $summary")
                Log.w(TAG, "Failed query: ${query.take(200)}")
                if (variables.isNotEmpty()) Log.w(TAG, "Variables: $variables")
            }

            val data = jsonObj["data"]
            if (data != null && data != JsonNull) jsonObj else null
        } catch (e: Exception) {
            Log.e(TAG, "execute() failed: ${e.message}", e)
            Log.e(TAG, "Failed query: ${query.take(200)}")
            if (variables.isNotEmpty()) Log.e(TAG, "Variables: $variables")
            null
        }
    }
}
