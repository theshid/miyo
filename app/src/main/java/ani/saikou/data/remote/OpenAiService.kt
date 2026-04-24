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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OpenAiService(private val apiKey: String) {

    companion object {
        private const val TAG = "OpenAiService"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val MODEL = "gpt-4o"

        val SYSTEM_PROMPT = """
            You are Miyo AI, a friendly and knowledgeable anime & manga assistant built into the Miyo app.

            You can help users with:
            - Finding anime or manga based on natural language descriptions (mood, themes, similarity to other titles)
            - Summarizing where they left off in a series ("Catch me up")
            - Answering questions about characters, plot, studios, and creators
            - Giving personalized recommendations based on their watch/read history

            Guidelines:
            - Be concise but enthusiastic — match the energy of an anime fan talking to a friend
            - When recommending titles, always include the full title and a one-line pitch
            - If asked to catch someone up, summarize without major spoilers unless they explicitly ask
            - Format lists with bullet points for readability
            - If you don't know something, say so rather than guessing
            - Keep responses under 300 words unless the user asks for more detail
        """.trimIndent()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    data class ChatMessage(
        val role: String, // "system", "user", or "assistant"
        val content: String,
    )

    suspend fun chat(messages: List<ChatMessage>): String {
        val body = buildJsonObject {
            put("model", MODEL)
            put("messages", buildJsonArray {
                for (msg in messages) {
                    add(buildJsonObject {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            })
            put("max_tokens", 1024)
            put("temperature", 0.7)
        }

        return try {
            val response = client.post(ENDPOINT) {
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
}
