package ani.saikou

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.data.local.db.ActivityEventEntity
import ani.saikou.di.AppModule
import ani.saikou.notifications.EpisodeCheckWorker
import ani.saikou.notifications.EpisodeNotificationChannel
import io.github.theshid.prettylog.Log
import io.github.theshid.prettylog.LogBreadcrumbs
import io.github.theshid.prettylog.LogLevel
import io.github.theshid.prettylog.PrettyLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MiyoApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Initialize the logging library BEFORE anything else — AppModule,
        // workers, and any other startup code may log during their own init.
        PrettyLog.init(
            isDebug = BuildConfig.DEBUG,
            defaultTag = "Miyo",
            minLevel = LogLevel.Debug,
        )
        AppModule.init(this)
        installCrashBreadcrumbs()
        EpisodeNotificationChannel.createChannel(this)
        scheduleEpisodeCheck()
        observeListChangesForAlarmReschedule()
        logDailySession()
    }

    /**
     * When the user adds/removes a show from their list, re-run the check
     * worker so the new entry gets an airing alarm (or the removed one loses
     * it). Without this the user would have to wait for the next 15-min tick.
     */
    private fun observeListChangesForAlarmReschedule() {
        appScope.launch {
            ListEventBus.events.collect { event ->
                if (event is ListEvent.ListEntryChanged) {
                    val request = OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build()
                    WorkManager.getInstance(this@MiyoApplication).enqueueUniqueWork(
                        "episode_check_list_change",
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
            }
        }
    }

    private fun logDailySession() {
        val dao = AppModule.activityEventDao()
        val startOfDayMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (dao.countByTypeSince("session", startOfDayMs) == 0) {
                    dao.insert(
                        ActivityEventEntity(
                            timestampMs = System.currentTimeMillis(),
                            type = "session",
                        )
                    )
                }
            } catch (_: Exception) { /* best-effort */ }
        }
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
