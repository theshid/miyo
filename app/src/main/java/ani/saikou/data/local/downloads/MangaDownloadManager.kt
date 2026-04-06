package ani.saikou.data.local.downloads

import android.content.Context
import ani.saikou.data.local.db.DownloadDao
import ani.saikou.data.local.db.DownloadEntity
import ani.saikou.data.local.db.DownloadedMangaEntity
import ani.saikou.data.remote.parsers.MangaDexParser
import ani.saikou.domain.model.MangaPage
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
    private val mangaDex = MangaDexParser()
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

        var downloadedCount = download.downloadedPages
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
                    downloadPage(page.imageUrl, pageFile)
                }
                downloadedCount++
                dao.updateProgress(downloadId, downloadedCount, "DOWNLOADING")
                onProgress(downloadedCount, pages.size)
            } catch (e: Exception) {
                dao.updateStatus(downloadId, "ERROR")
                success = false
                break
            }
        }

        if (success) {
            dao.updateProgress(downloadId, pages.size, "COMPLETED")
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

    private fun downloadPage(url: String, destination: File) {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Saikou/2.0")
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.getInputStream().use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
