package ani.saikou.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class AnilistApi(private val tokenProvider: () -> String?) {

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"
        const val CLIENT_ID = 6818
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            url(ENDPOINT)
            contentType(ContentType.Application.Json)
        }
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun execute(query: String, variables: String = ""): JsonObject? {
        return try {
            val response = client.post {
                setBody("""{"query":"$query","variables":"$variables"}""")
                tokenProvider()?.let { token ->
                    header("Authorization", "Bearer $token")
                }
            }
            val responseText = response.bodyAsText()
            val jsonObj = json.decodeFromString<JsonObject>(responseText)
            val data = jsonObj["data"]
            if (data != null && data != JsonNull) jsonObj else null
        } catch (e: Exception) {
            null
        }
    }
}
