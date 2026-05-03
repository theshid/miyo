package ani.saikou.data.remote

import android.os.Build
import ani.saikou.domain.source.FeedbackService
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
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Android impl of [FeedbackService] — posts feedback to a Discord webhook.
 *
 * The webhook URL and app-version strings are injected at construction so
 * this module doesn't need its own BuildConfig. DI in :app reads them
 * from the application BuildConfig and hands them in.
 */
class FeedbackServiceImpl(
    private val webhookUrl: String,
    private val appVersionName: String,
    private val appVersionCode: Int,
) : FeedbackService {
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
     * POSTs the feedback as a Discord embed. Captures device + app context
     * automatically so each report carries enough info to triage. No PII.
     */
    override suspend fun submit(
        category: FeedbackService.Category,
        message: String,
    ): FeedbackService.Result =
        withContext(Dispatchers.IO) {
            if (webhookUrl.isBlank()) {
                return@withContext FeedbackService.Result.Failure("Feedback isn't configured for this build.")
            }

            val payload =
                buildJsonObject {
                    putJsonArray("embeds") {
                        addJsonObject {
                            put("title", "${category.emoji} ${category.title}")
                            put("description", message.trim())
                            put("color", category.color)
                            putJsonArray("fields") {
                                addJsonObject {
                                    put("name", "App version")
                                    put("value", "$appVersionName ($appVersionCode)")
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
                            put(
                                "timestamp",
                                java.time.Instant
                                    .now()
                                    .toString(),
                            )
                        }
                    }
                }.toString()

            try {
                val response =
                    client.post(webhookUrl) {
                        contentType(ContentType.Application.Json)
                        header("Accept", "application/json")
                        setBody(payload)
                    }
                // Discord returns 204 No Content on success, or 4xx with an error body.
                val status = response.status.value
                if (status in 200..299) {
                    FeedbackService.Result.Success
                } else {
                    Log.w(tag = "Feedback", message = "Discord webhook returned HTTP $status: ${response.bodyAsText()}")
                    FeedbackService.Result.Failure("Couldn't send (HTTP $status). Try again later.")
                }
            } catch (e: Exception) {
                Log.e(tag = "Feedback", message = "Webhook POST failed", throwable = e)
                FeedbackService.Result.Failure("Couldn't reach the server. Check your connection.")
            }
        }
}
