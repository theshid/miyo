package ani.saikou

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ani.saikou.di.AppModule
import ani.saikou.logging.LogBreadcrumbs
import ani.saikou.logging.Log
import ani.saikou.notifications.EpisodeCheckWorker
import ani.saikou.notifications.EpisodeNotificationChannel
import java.util.concurrent.TimeUnit

class SaikouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        installCrashBreadcrumbs()
        EpisodeNotificationChannel.createChannel(this)
        scheduleEpisodeCheck()
    }

    private fun installCrashBreadcrumbs() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Dump the last 50 log entries to a file before crashing
            try {
                Log.wtf(tag = "CRASH", message = "Uncaught exception on ${thread.name}: ${throwable.message}")
                LogBreadcrumbs.dumpToFile(this)
            } catch (_: Exception) { /* best-effort */ }
            // Forward to the default handler (crash dialog / process kill)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun scheduleEpisodeCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Periodic check — every 15 min (WorkManager's minimum interval)
        val periodicRequest = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
            15, TimeUnit.MINUTES,
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "episode_check",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        // Fire one immediately on app start so we seed the baseline / catch
        // up on missed episodes without waiting 15 minutes
        val immediateRequest = OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "episode_check_now",
            ExistingWorkPolicy.KEEP,
            immediateRequest,
        )
    }
}
