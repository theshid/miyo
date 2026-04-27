package ani.saikou.data.local.downloads

import android.content.Context
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.DownloadEntity
import ani.saikou.data.local.db.DownloadedMangaEntity
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.di.AppModule
import ani.saikou.domain.model.MangaPage
import io.github.theshid.prettylog.Log as PLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class MangaDownloadManager(
    private val context: Context,
    private val dao: DownloadDao,
) {
    private val mangaDex = MangaDexParser(AppModule.logger())
    private val concurrencySemaphore = Semaphore(3) // Max 3 concurrent page downloads

    private val _activeDownloadId = MutableStateFlow<String?>(null)
    val activeDownloadId: StateFlow<String?> = _activeDownloadId

    private val downloadDir: File
        get() = File(context.getExternalFilesDir(null), "downloads").also { it.mkdirs() }

    /**
     * Queue a chapter for download.
     */
    suspend fun queueDownload(
        mangaId: Int,
        mangaTitle: String,
        coverUrl: String?,
        chapterKey: String,
        chapterNumber: Int,
        chapterName: String,
        sourceId: String,
        totalPages: Int,
    ) {
        val id = "${mangaId}_$chapterKey"
        val existing = dao.getDownload(id)
        if (existing?.status == "COMPLETED") return // Already downloaded

        dao.insertDownloadedManga(
            DownloadedMangaEntity(mangaId = mangaId, title = mangaTitle, coverUrl = coverUrl, sourceId = sourceId)
        )

        dao.insertDownload(
            DownloadEntity(
                id = id,
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                chapterKey = chapterKey,
                chapterNumber = chapterNumber,
                chapterName = chapterName,
                sourceId = sourceId,
                status = "QUEUED",
                totalPages = totalPages,
                downloadedPages = existing?.downloadedPages ?: 0,
            )
        )
    }

    /**
     * Download pages for a chapter. Call this from the foreground service.
     */
    suspend fun downloadChapter(
        downloadId: String,
        pages: List<MangaPage>,
        onProgress: (downloaded: Int, total: Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val download = dao.getDownload(downloadId) ?: return@withContext false
        _activeDownloadId.value = downloadId

        val chapterDir = File(downloadDir, "${download.mangaId}/${download.chapterKey}")
        chapterDir.mkdirs()

        dao.updateStatus(downloadId, "DOWNLOADING")
        // Reflects how many of `pages.size` are actually on disk. Starts at 0
        // so resumed runs don't double-count: the loop below increments for
        // both skipped (already-on-disk) and newly-fetched pages.
        var downloadedCount = 0
        var success = true

        for (page in pages) {
            // Skip already downloaded pages
            val pageFile = File(chapterDir, "%03d.jpg".format(page.index + 1))
            if (pageFile.exists() && pageFile.length() > 0) {
                downloadedCount++
                continue
            }

            // Check if paused/cancelled
            val current = dao.getDownload(downloadId)
            if (current?.status == "PAUSED" || current == null) {
                _activeDownloadId.value = null
                return@withContext false
            }

            try {
                concurrencySemaphore.withPermit {
                    downloadPage(page.imageUrl, pageFile, page.headers)
                }
                downloadedCount++
                dao.updateProgress(downloadId, downloadedCount, "DOWNLOADING")
                onProgress(downloadedCount, pages.size)
            } catch (e: Exception) {
                PLog.e(
                    tag = "Download",
                    message = "Page ${page.index + 1}/${pages.size} of '${download.mangaTitle}' ch ${download.chapterNumber} failed after retries: ${e.javaClass.simpleName}: ${e.message}",
                    throwable = e,
                )
                dao.updateStatus(downloadId, "ERROR")
                success = false
                break
            }
        }

        if (success) {
            dao.updateProgress(downloadId, pages.size, "COMPLETED")
            // Measure the actual bytes written so the reader's "Download next N"
            // estimate is based on real data instead of a fixed guess.
            val totalBytes = chapterDir.walkBottomUp()
                .filter { it.isFile }
                .sumOf { it.length() }
            dao.updateFileSize(downloadId, totalBytes)
        }

        _activeDownloadId.value = null
        success
    }

    /**
     * Get chapter pages from local storage if downloaded.
     */
    fun getLocalPages(mangaId: Int, chapterKey: String): List<MangaPage>? {
        val chapterDir = File(downloadDir, "$mangaId/$chapterKey")
        if (!chapterDir.exists()) return null

        val files = chapterDir.listFiles()
            ?.filter { it.extension in listOf("jpg", "png", "webp") }
            ?.sortedBy { it.name }
            ?: return null

        if (files.isEmpty()) return null

        return files.mapIndexed { index, file ->
            MangaPage(index = index, imageUrl = file.toURI().toString())
        }
    }

    /**
     * Check if a chapter is fully downloaded.
     */
    suspend fun isChapterDownloaded(mangaId: Int, chapterKey: String): Boolean {
        val download = dao.getDownloadByChapter(mangaId, chapterKey)
        return download?.status == "COMPLETED"
    }

    suspend fun pauseDownload(downloadId: String) {
        dao.updateStatus(downloadId, "PAUSED")
    }

    suspend fun resumeDownload(downloadId: String) {
        dao.updateStatus(downloadId, "QUEUED")
    }

    suspend fun cancelDownload(downloadId: String) {
        val download = dao.getDownload(downloadId) ?: return
        dao.deleteDownload(downloadId)
        // Clean up files
        val chapterDir = File(downloadDir, "${download.mangaId}/${download.chapterKey}")
        chapterDir.deleteRecursively()
    }

    suspend fun deleteAllForManga(mangaId: Int) {
        dao.deleteAllForManga(mangaId)
        dao.deleteDownloadedManga(mangaId)
        File(downloadDir, "$mangaId").deleteRecursively()
    }

    fun getStorageUsed(): Long {
        return downloadDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    fun getAvailableSpace(): Long {
        return downloadDir.freeSpace
    }

    private suspend fun downloadPage(url: String, destination: File, headers: Map<String, String> = emptyMap()) {
        // Manga CDNs (mangap, mgcdn, etc.) periodically reset connections —
        // either via load-balancer churn or rate-limit drops. Without a retry,
        // a single SocketException kills the whole chapter download. 3 attempts
        // with backoff (500ms / 1500ms / 4500ms) clears almost all transient
        // blips. After exhaustion we re-throw so the caller marks ERROR.
        val maxAttempts = 3
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            if (attempt > 1) {
                kotlinx.coroutines.delay(500L * (1 shl (attempt - 1)))
            }
            try {
                fetchPage(url, destination, headers)
                return
            } catch (e: java.net.SocketException) {
                lastException = e
            } catch (e: java.net.SocketTimeoutException) {
                lastException = e
            } catch (e: java.io.IOException) {
                lastException = e
            }
        }
        throw lastException ?: java.io.IOException("Failed after $maxAttempts attempts")
    }

    private fun fetchPage(url: String, destination: File, headers: Map<String, String>) {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Miyo/2.0")
        // MangaPill's CDN refuses requests without a Referer header; passing
        // page.headers from the parser handles this generically.
        for ((k, v) in headers) {
            connection.setRequestProperty(k, v)
        }
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.getInputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
