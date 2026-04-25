package ani.saikou.data.remote

import android.os.Build
import ani.saikou.BuildConfig
import io.github.theshid.prettylog.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Posts user feedback to a Discord webhook. The webhook URL is provided via
 * [BuildConfig.DISCORD_FEEDBACK_WEBHOOK] (sourced from `local.properties`,
 * which is gitignored). Renders the feedback as a Discord embed so the
 * channel shows it as a tidy card with category color, message body, and
 * device/version footer fields.
 */
class FeedbackService {

    private val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    enum class Category(val title: String, val emoji: String, val color: Int) {
        BUG("Bug report", "🐞", 0xE74C3C),         // red
        FEATURE("Feature request", "✨", 0xF1C40F), // yellow
        GENERAL("General feedback", "💬", 0x3498DB), // blue
    }

    /** Result of [submit]. The screen turns this into a snackbar/banner. */
    sealed class Result {
        data object Success : Result()
        data class Failure(val reason: String) : Result()
    }

    /**
     * POSTs the feedback as a Discord embed. Captures device + app context
     * automatically so each report carries enough info to triage. No PII.
     */
    suspend fun submit(category: Category, message: String): Result = withContext(Dispatchers.IO) {
        val webhookUrl = BuildConfig.DISCORD_FEEDBACK_WEBHOOK
        if (webhookUrl.isBlank()) {
            return@withContext Result.Failure("Feedback isn't configured for this build.")
        }

        val payload = buildJsonObject {
            putJsonArray("embeds") {
                addJsonObject {
                    put("title", "${category.emoji} ${category.title}")
                    put("description", message.trim())
                    put("color", category.color)
                    putJsonArray("fields") {
                        addJsonObject {
                            put("name", "App version")
                            put("value", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                            put("inline", true)
                        }
                        addJsonObject {
                            put("name", "Device")
                            put("value", "${Build.MANUFACTURER} ${Build.MODEL}")
                            put("inline", true)
                        }
                        addJsonObject {
                            put("name", "Android")
                            put("value", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                            put("inline", true)
                        }
                    }
                    put("timestamp", java.time.Instant.now().toString())
                }
            }
        }.toString()

        try {
            val response = client.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                setBody(payload)
            }
            // Discord returns 204 No Content on success, or 4xx with an error body.
            val status = response.status.value
            if (status in 200..299) {
                Result.Success
            } else {
                Log.w(tag = "Feedback", message = "Discord webhook returned HTTP $status: ${response.bodyAsText()}")
                Result.Failure("Couldn't send (HTTP $status). Try again later.")
            }
        } catch (e: Exception) {
            Log.e(tag = "Feedback", message = "Webhook POST failed", throwable = e)
            Result.Failure("Couldn't reach the server. Check your connection.")
        }
    }
}
