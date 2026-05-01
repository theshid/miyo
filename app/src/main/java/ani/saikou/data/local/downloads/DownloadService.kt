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
import ani.saikou.data.source.manga.MangaDexParser
import ani.saikou.data.source.manga.MangaPillParser
import ani.saikou.domain.model.MangaPage
import ani.saikou.domain.model.pickBestMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DownloadService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private var downloadJob: Job? = null

    // Service is constructed by the Android framework; Koin's `by inject()`
    // resolves through the Service-as-KoinComponent extension provided by
    // koin-android — no explicit KoinComponent declaration needed.
    private val mangaDex: MangaDexParser by inject()
    private val mangaPill: MangaPillParser by inject()
    private lateinit var downloadManager: MangaDownloadManager

    companion object {
        const val CHANNEL_ID = "saikou_downloads"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PAUSE = "ani.saikou.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "ani.saikou.CANCEL_DOWNLOAD"

        /** Cap on service-level auto-retries per row before we leave it ERROR. */
        const val MAX_AUTO_RETRY_ATTEMPTS = 3

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
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
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
        downloadJob =
            scope.launch {
                val dao = SaikouDatabase.getInstance(this@DownloadService).downloadDao()
                // Pick up previous failures that haven't been retried too many times
                // already — every time the service starts (a new download, app launch,
                // etc.) we get a fresh shot at chapters that hit transient issues.
                dao.requeueRetryableErrors(maxAttempts = MAX_AUTO_RETRY_ATTEMPTS)
                val pending = dao.getPendingDownloads()

                if (pending.isEmpty()) {
                    showCompletionNotification()
                    stopSelf()
                    return@launch
                }

                for (download in pending) {
                    // Try MangaDex first, fall back to MangaPill — same order as the reader.
                    val resolved = resolvePages(download.mangaTitle, download.chapterNumber)
                    if (resolved == null) {
                        dao.updateStatus(download.id, "ERROR")
                        continue
                    }
                    val pages = resolved.pages

                    // Persist page count AND the resolved sourceId so future reads of
                    // this downloaded chapter save history with a real source context
                    // instead of the empty placeholder from queueing. The reader reads
                    // `sourceId` off DownloadEntity, so updating the row here is enough.
                    val needsUpdate =
                        download.totalPages != pages.size ||
                            (download.sourceId.isEmpty() && resolved.sourceId.isNotEmpty())
                    if (needsUpdate) {
                        dao.insertDownload(
                            download.copy(
                                totalPages = pages.size,
                                sourceId = resolved.sourceId.ifEmpty { download.sourceId },
                            ),
                        )
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

    private data class ResolvedChapter(
        val pages: List<MangaPage>,
        val sourceId: String,
    )

    /**
     * Resolves the page list for a chapter. Tries MangaDex first; falls back
     * to MangaPill (the parser the reader uses for licensed/unavailable
     * titles). Page objects keep their `headers` so the downloader can apply
     * the Referer that MangaPill's CDN requires. The resolved sourceId is
     * returned so the caller can persist it back onto the download row.
     */
    private suspend fun resolvePages(
        mangaTitle: String,
        chapterNumber: Int,
    ): ResolvedChapter? {
        if (chapterNumber < 0) return null

        // MangaDex — exact-title preference via pickBestMatch handles the
        // case where relevance ranking floats colored re-releases or
        // spin-offs above the canonical entry.
        runCatching {
            val source = mangaDex.search(mangaTitle).pickBestMatch(mangaTitle)
            if (source != null) {
                val chapters = mangaDex.getChapters(source.id)
                val chapter = chapters.find { it.number.toInt() == chapterNumber }
                if (chapter != null) {
                    val pages = mangaDex.getPages(chapter.id)
                    if (pages.isNotEmpty()) return ResolvedChapter(pages, source.id)
                }
            }
        }

        // MangaPill fallback.
        runCatching {
            val source = mangaPill.search(mangaTitle).pickBestMatch(mangaTitle)
            if (source != null) {
                val chapters = mangaPill.getChapters(source.id)
                val chapter = chapters.find { it.number.toInt() == chapterNumber }
                if (chapter != null) {
                    val pages = mangaPill.getPages(chapter.id)
                    if (pages.isNotEmpty()) return ResolvedChapter(pages, source.id)
                }
            }
        }

        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
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

    private fun buildNotification(
        text: String,
        progress: Int = 0,
        max: Int = 0,
    ): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

        val pauseIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, DownloadService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_IMMUTABLE,
            )
        val cancelIntent =
            PendingIntent.getService(
                this,
                2,
                Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Saikou Downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel", cancelIntent)
            .apply {
                if (max > 0) setProgress(max, progress, false)
            }.build()
    }

    private fun updateNotification(
        title: String,
        downloaded: Int,
        total: Int,
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification("$title — $downloaded/$total pages", downloaded, total))
    }

    private fun showCompletionNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
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
