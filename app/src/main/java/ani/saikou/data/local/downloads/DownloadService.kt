package ani.saikou.data.local.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ani.saikou.MainActivity
import ani.saikou.R
import ani.saikou.data.local.db.SaikouDatabase
import ani.saikou.data.remote.parsers.MangaDexParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var downloadJob: Job? = null

    private lateinit var downloadManager: MangaDownloadManager
    private lateinit var mangaDex: MangaDexParser

    companion object {
        const val CHANNEL_ID = "saikou_downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "ani.saikou.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "ani.saikou.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val dao = SaikouDatabase.getInstance(this).downloadDao()
        downloadManager = MangaDownloadManager(this, dao)
        mangaDex = MangaDexParser()
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                scope.launch {
                    downloadManager.activeDownloadId.value?.let { downloadManager.pauseDownload(it) }
                }
            }
            ACTION_CANCEL -> {
                scope.launch {
                    downloadManager.activeDownloadId.value?.let { downloadManager.cancelDownload(it) }
                }
                stopSelf()
            }
            else -> processQueue()
        }
        return START_NOT_STICKY
    }

    private fun processQueue() {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val dao = SaikouDatabase.getInstance(this@DownloadService).downloadDao()
            val pending = dao.getPendingDownloads()

            if (pending.isEmpty()) {
                showCompletionNotification()
                stopSelf()
                return@launch
            }

            for (download in pending) {
                // Resolve pages from MangaDex
                val sources = mangaDex.search(download.mangaTitle)
                val source = sources.firstOrNull() ?: continue
                val chapters = mangaDex.getChapters(source.id)
                val chapter = chapters.find {
                    it.number.toInt().toString() == download.chapterKey ||
                    it.name.contains(download.chapterKey)
                } ?: continue

                val pages = mangaDex.getPages(chapter.id)
                if (pages.isEmpty()) {
                    dao.updateStatus(download.id, "ERROR")
                    continue
                }

                // Update total pages if needed
                if (download.totalPages != pages.size) {
                    dao.insertDownload(download.copy(totalPages = pages.size))
                }

                updateNotification("${download.mangaTitle} — Ch. ${download.chapterKey}", 0, pages.size)

                downloadManager.downloadChapter(
                    downloadId = download.id,
                    pages = pages,
                    onProgress = { downloaded, total ->
                        updateNotification("${download.mangaTitle} — Ch. ${download.chapterKey}", downloaded, total)
                    },
                )
            }

            showCompletionNotification()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Manga chapter downloads"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int = 0, max: Int = 0): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this, 2,
            Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Saikou Downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .apply {
                if (max > 0) setProgress(max, progress, false)
            }
            .build()
    }

    private fun updateNotification(title: String, downloaded: Int, total: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("$title — $downloaded/$total pages", downloaded, total))
    }

    private fun showCompletionNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloads Complete")
            .setContentText("All chapters have been downloaded")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}
