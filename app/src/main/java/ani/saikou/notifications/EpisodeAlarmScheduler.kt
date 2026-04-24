package ani.saikou.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import io.github.theshid.prettylog.Log

/**
 * Schedules one `AlarmManager.setAndAllowWhileIdle` alarm per upcoming airing.
 * Using `setAndAllowWhileIdle` (inexact, ±9 min in Doze) instead of exact alarms
 * so we don't need the SCHEDULE_EXACT_ALARM permission. AniList airing times
 * already slip by 5-30 minutes, so inexact is plenty precise for this use.
 *
 * Alarms are re-scheduled on every EpisodeCheckWorker run (every app open
 * + the periodic safety net), so schedule drift from the AniList side is
 * picked up automatically.
 */
class EpisodeAlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "EpisodeAlarmScheduler"
        private const val PREFS_NAME = "episode_alarms"
        private const val KEY_SCHEDULED_IDS = "scheduled_ids"
        // A small buffer so the alarm lands just *after* the stated airing time.
        // AniList's `airingAt` is the start of the episode; we want the user to
        // be pinged when it's actually available on streaming sites.
        private const val AIRING_BUFFER_MS = 120_000L // 2 min
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Replaces all previously scheduled alarms with fresh ones for the given
     * airings. Alarms in the past are skipped.
     */
    fun rescheduleAll(airings: List<Airing>) {
        val previouslyScheduled = prefs.getStringSet(KEY_SCHEDULED_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .toSet()

        // Cancel everything we scheduled last time — handles removed shows and
        // updated airing times atomically.
        for (mediaId in previouslyScheduled) {
            cancelAlarmFor(mediaId)
        }

        val now = System.currentTimeMillis()
        val newlyScheduled = mutableSetOf<Int>()

        for (airing in airings) {
            val fireAt = airing.airingTimeMs + AIRING_BUFFER_MS
            if (fireAt <= now) continue // already aired — nothing to notify about

            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    fireAt,
                    buildPendingIntent(airing),
                )
                newlyScheduled.add(airing.mediaId)
            } catch (e: SecurityException) {
                Log.w(tag = TAG, message = "Alarm scheduling blocked for ${airing.title}: ${e.message}")
            }
        }

        prefs.edit {
            putStringSet(KEY_SCHEDULED_IDS, newlyScheduled.map { it.toString() }.toSet())
        }

        // Only keep the BOOT_COMPLETED receiver enabled when we actually have
        // alarms to re-schedule after boot. Otherwise Android wakes the app on
        // every boot for nothing.
        setBootReceiverEnabled(newlyScheduled.isNotEmpty())

        Log.i(
            tag = TAG,
            message = "Rescheduled alarms: cancelled ${previouslyScheduled.size}, " +
                "set ${newlyScheduled.size} (skipped ${airings.size - newlyScheduled.size} past/failed)",
        )
    }

    private fun setBootReceiverEnabled(enabled: Boolean) {
        val component = ComponentName(context, BootCompletedReceiver::class.java)
        val desired = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val current = context.packageManager.getComponentEnabledSetting(component)
        if (current != desired) {
            context.packageManager.setComponentEnabledSetting(
                component,
                desired,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    fun cancelAlarmFor(mediaId: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            mediaId,
            Intent(context, EpisodeAiringReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun buildPendingIntent(airing: Airing): PendingIntent {
        val intent = Intent(context, EpisodeAiringReceiver::class.java).apply {
            putExtra(EpisodeAiringReceiver.EXTRA_MEDIA_ID, airing.mediaId)
            putExtra(EpisodeAiringReceiver.EXTRA_EPISODE, airing.episode)
            putExtra(EpisodeAiringReceiver.EXTRA_TITLE, airing.title)
            putExtra(EpisodeAiringReceiver.EXTRA_COVER_URL, airing.coverUrl)
        }
        return PendingIntent.getBroadcast(
            context,
            airing.mediaId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    data class Airing(
        val mediaId: Int,
        val title: String,
        val coverUrl: String?,
        val episode: Int,
        val airingTimeMs: Long,
    )
}
