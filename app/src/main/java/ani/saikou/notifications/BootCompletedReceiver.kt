package ani.saikou.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.theshid.prettylog.Log

/**
 * AlarmManager alarms are cleared when the device reboots. This receiver
 * re-enqueues the episode check worker on boot, which will refetch the
 * user's list and re-schedule every upcoming airing alarm.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        Log.i(tag = "BootCompletedReceiver", message = "Re-scheduling episode alarms after boot")

        val request =
            OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "episode_check_boot",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
