package ani.saikou.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ani.saikou.di.AppModule
import ani.saikou.logging.Log
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic background worker that checks the user's CURRENT anime list for new
 * episodes. Tracks the last-known aired episode per show; if AniList reports a
 * higher episode number, fires a local notification.
 */
class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "EpisodeCheckWorker"
        private const val PREFS_NAME = "episode_check"
        private const val KEY_LAST_EP_PREFIX = "last_ep_"
        private const val KEY_FIRST_RUN = "first_run_done"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val repository = AppModule.repository()
            if (!repository.isLoggedIn()) {
                Log.d(tag = TAG, message = "Not logged in — skipping check")
                return@withContext Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val firstRun = !prefs.getBoolean(KEY_FIRST_RUN, false)

            // Fetch user's currently watching anime
            val watching = repository.getUserAnimeList("CURRENT")
            Log.d(tag = TAG, message = "Checking ${watching.size} anime on CURRENT list")

            var notified = 0
            val editor = prefs.edit()

            for (media in watching) {
                // `nextAiringEpisode` in our parser = last AIRED episode (AniList's upcoming - 1)
                val lastAired = media.nextAiringEpisode ?: continue
                val key = "$KEY_LAST_EP_PREFIX${media.id}"
                val previouslyKnownEp = prefs.getInt(key, -1)

                Log.d(
                    tag = TAG,
                    message = "${media.displayTitle}: last aired = $lastAired, previously known = $previouslyKnownEp",
                )

                when {
                    // First time seeing this anime — just record, don't notify
                    previouslyKnownEp == -1 -> {
                        editor.putInt(key, lastAired)
                    }
                    // New episode detected — notify!
                    lastAired > previouslyKnownEp && !firstRun -> {
                        val cover = loadCoverBitmap(media.cover)
                        EpisodeNotificationChannel.showEpisodeNotification(
                            context = applicationContext,
                            mediaId = media.id,
                            title = media.displayTitle,
                            episode = lastAired,
                            coverBitmap = cover,
                        )
                        editor.putInt(key, lastAired)
                        notified++
                        Log.i(tag = TAG, message = "✓ Notified for ${media.displayTitle} ep $lastAired")
                    }
                    // First run after install — seed baseline without notifying
                    firstRun -> {
                        editor.putInt(key, lastAired)
                    }
                }
            }

            if (firstRun) {
                editor.putBoolean(KEY_FIRST_RUN, true)
                Log.i(tag = TAG, message = "First run — seeded baseline for ${watching.size} anime, no notifications fired")
            }

            editor.apply()
            Log.i(tag = TAG, message = "Check complete — $notified notifications sent")

            Result.success()
        } catch (e: Exception) {
            Log.e(tag = TAG, message = "Episode check failed", throwable = e)
            Result.retry()
        }
    }

    private suspend fun loadCoverBitmap(url: String?): Bitmap? {
        if (url.isNullOrEmpty()) return null
        return try {
            val loader = ImageLoader(applicationContext)
            val request = ImageRequest.Builder(applicationContext)
                .data(url)
                .allowHardware(false)
                .size(128, 128)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
