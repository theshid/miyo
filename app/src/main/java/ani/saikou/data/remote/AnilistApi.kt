package ani.saikou.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnilistApi(private val tokenProvider: () -> String?) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val CLIENT_ID = 6818
    }

    suspend fun execute(query: String, variables: String = ""): JsonObject? = withContext(Dispatchers.IO) {
        try {
            val body = """{"query":"$query","variables":"$variables"}"""
            val requestBuilder = Request.Builder()
                .url(ENDPOINT)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            tokenProvider()?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val jsonObj = json.decodeFromString<JsonObject>(responseBody)
            val data = jsonObj["data"]
            if (data != null && data != JsonNull) jsonObj else null
        } catch (e: Exception) {
            null
        }
    }
}
