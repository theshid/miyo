package ani.saikou.data.remote

import ani.saikou.domain.model.ChatMessage
import ani.saikou.domain.source.AiChatService
import io.github.theshid.prettylog.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * OpenAI-backed [AiChatService]. Owns its own [HttpClient] (rather than the
 * shared one from DI) because chat completions can take 30s+ — the global
 * timeouts are tuned for short-poll API calls and would cut a slow stream
 * mid-response.
 */
class AiChatServiceImpl(
    private val apiKey: String,
) : AiChatService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(60, TimeUnit.SECONDS)
                }
            }
        }

    override suspend fun chat(messages: List<ChatMessage>): String {
        val body =
            buildJsonObject {
                put("model", MODEL)
                put(
                    "messages",
                    buildJsonArray {
                        for (msg in messages) {
                            add(
                                buildJsonObject {
                                    put("role", msg.role)
                                    put("content", msg.content)
                                },
                            )
                        }
                    },
                )
                put("max_tokens", 1024)
                put("temperature", 0.7)
            }

        return try {
            val response =
                client.post(ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(body.toString())
                }

            val responseText = response.bodyAsText()
            val responseJson = json.parseToJsonElement(responseText).jsonObject

            // Check for error
            responseJson["error"]?.let { error ->
                val errorMsg = error.jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown API error"
                Log.e(TAG, "OpenAI API error: $errorMsg")
                return "Sorry, I ran into an issue: $errorMsg"
            }

            responseJson["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call OpenAI", e)
            "Sorry, I couldn't connect right now. Check your internet and try again."
        }
    }

    companion object {
        private const val TAG = "AiChatService"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"
    }
}
