package ani.saikou.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.theshid.prettylog.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager shortly after an anime's airing time. Loads the
 * cover art off the main thread and shows the notification. No AniList
 * round-trip — we trust the airing time that was stored at schedule time.
 */
class EpisodeAiringReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "EpisodeAiringReceiver"
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_TITLE = "title"
        const val EXTRA_COVER_URL = "cover_url"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val mediaId = intent.getIntExtra(EXTRA_MEDIA_ID, -1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, -1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL)

        if (mediaId < 0 || episode < 0) {
            Log.w(tag = TAG, message = "Missing extras — mediaId=$mediaId episode=$episode")
            return
        }

        // goAsync() lets us do IO (cover fetch) off the main thread while
        // keeping the broadcast alive up to ~10s.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val cover = loadCoverBitmap(context, coverUrl)
                EpisodeNotificationChannel.showEpisodeNotification(
                    context = context,
                    mediaId = mediaId,
                    title = title,
                    episode = episode,
                    coverBitmap = cover,
                )
                Log.i(tag = TAG, message = "Notified $title ep $episode")
            } catch (e: Exception) {
                Log.e(tag = TAG, message = "Failed to show notification for $title", throwable = e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun loadCoverBitmap(
        context: Context,
        url: String?,
    ): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return try {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(128, 128)
                    .build()
            val result = ImageLoader(context).execute(request)
            (result as? SuccessResult)?.image?.toBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
