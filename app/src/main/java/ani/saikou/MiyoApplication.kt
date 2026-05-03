package ani.saikou

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ani.saikou.data.android.di.dataAndroidModule
import ani.saikou.data.di.dataModule
import ani.saikou.data.local.db.ActivityEventDao
import ani.saikou.data.local.db.ActivityEventEntity
import ani.saikou.di.appModule
import ani.saikou.domain.event.ListEvent
import ani.saikou.domain.event.ListEventBus
import ani.saikou.notifications.EpisodeCheckWorker
import ani.saikou.notifications.EpisodeNotificationChannel
import ani.saikou.platform.android.di.platformAndroidModule
import io.github.theshid.prettylog.DefaultLoggingService
import io.github.theshid.prettylog.Log
import io.github.theshid.prettylog.LogBreadcrumbs
import io.github.theshid.prettylog.LogLevel
import io.github.theshid.prettylog.LoggingService
import io.github.theshid.prettylog.PrettyLog
import io.github.theshid.prettylog.PrettyLoggingService
import io.github.theshid.prettylog.dumpToFile
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MiyoApplication : Application() {
    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activityEventDao: ActivityEventDao by inject()

    override fun onCreate() {
        super.onCreate()
        // Initialize the logging library BEFORE anything else — AppModule,
        // workers, and any other startup code may log during their own init.
        // The custom service forwards every log call into Sentry as a breadcrumb
        // so a crash report carries the last ~100 log lines as context.
        val baseService: LoggingService =
            if (BuildConfig.DEBUG) {
                PrettyLoggingService(defaultTag = "Miyo", minLevel = LogLevel.Debug)
            } else {
                DefaultLoggingService(defaultTag = "Miyo")
            }
        PrettyLog.init(
            isDebug = BuildConfig.DEBUG,
            defaultTag = "Miyo",
            minLevel = LogLevel.Debug,
            custom = SentryBreadcrumbLoggingService(baseService),
        )
        // Koin owns the DI graph. Every consumer (ViewModels, Activities,
        // Service, Worker, Composables) resolves through the modules below.
        startKoin {
            // ERROR keeps the noise floor low in release; DEBUG flips on
            // verbose binding traces in dev builds.
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)
            androidContext(this@MiyoApplication)
            modules(
                appModule,
                platformAndroidModule,
                dataModule,
                dataAndroidModule,
            )
        }
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
                    val request =
                        OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                            .setConstraints(
                                Constraints
                                    .Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build(),
                            ).build()
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
        val dao = activityEventDao
        val startOfDayMs =
            LocalDate
                .now()
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
                        ),
                    )
                }
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    private fun installCrashBreadcrumbs() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Dump the last 50 log entries to a file before crashing.
            // Sentry's own UncaughtExceptionHandlerIntegration captures the crash
            // automatically — we just want the local breadcrumb file as a backup.
            try {
                Log.wtf(tag = "CRASH", message = "Uncaught exception on ${thread.name}: ${throwable.message}")
                LogBreadcrumbs.dumpToFile(this)
            } catch (_: Exception) {
                // best-effort
            }
            // Forward to the default handler (Sentry's wrapper, then crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun scheduleEpisodeCheck() {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        // Periodic check — every 15 min (WorkManager's minimum interval)
        val periodicRequest =
            PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
                15,
                TimeUnit.MINUTES,
            ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "episode_check",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest,
        )

        // Fire one immediately on app start so we seed the baseline / catch
        // up on missed episodes without waiting 15 minutes
        val immediateRequest =
            OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "episode_check_now",
            ExistingWorkPolicy.KEEP,
            immediateRequest,
        )
    }
}

/**
 * Wraps a [LoggingService] and mirrors every Info+ log call into Sentry as a
 * [Breadcrumb]. Sentry attaches the most recent ~100 breadcrumbs to each event
 * automatically, so a crash report shows what the app was doing right before
 * it died (network calls, navigation, error states) without us having to
 * instrument anything further. Debug/Verbose are skipped to keep the trail
 * focused on user-facing events.
 */
private class SentryBreadcrumbLoggingService(
    private val delegate: LoggingService,
) : LoggingService {
    override fun log(
        message: String,
        tag: String?,
        level: LogLevel,
        error: Throwable?,
    ) {
        delegate.log(message, tag, level, error)

        if (level == LogLevel.Debug) return

        try {
            val crumb =
                Breadcrumb().apply {
                    this.level =
                        when (level) {
                            LogLevel.Debug -> SentryLevel.DEBUG
                            LogLevel.Info -> SentryLevel.INFO
                            LogLevel.Warning -> SentryLevel.WARNING
                            LogLevel.Error -> SentryLevel.ERROR
                            LogLevel.Critical -> SentryLevel.FATAL
                        }
                    this.category = tag ?: "log"
                    this.message = message
                    if (error != null) setData("throwable", error.toString())
                }
            Sentry.addBreadcrumb(crumb)
        } catch (_: Exception) {
            // best-effort
        }
    }
}
