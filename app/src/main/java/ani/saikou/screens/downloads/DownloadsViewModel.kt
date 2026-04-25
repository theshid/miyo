package ani.saikou.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.db.DownloadEntity
import ani.saikou.data.local.db.DownloadedMangaEntity
import ani.saikou.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadsViewModel : ViewModel() {

    private val dao = AppModule.downloadDao()
    private val manager = AppModule.downloadManager()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                dao.getAllDownloadedManga(),
                dao.getAllDownloads(),
            ) { manga, downloads ->
                val grouped = manga.map { m ->
                    MangaWithDownloads(
                        manga = m,
                        chapters = downloads.filter { it.mangaId == m.mangaId },
                    )
                }.filter { it.chapters.isNotEmpty() }

                // Re-query the "read" set every time downloads change. Cheap —
                // it's a small index-backed join.
                val readChapters = runCatching { dao.getReadCompletedDownloads() }.getOrDefault(emptyList())

                DownloadsUiState(
                    mangaList = grouped,
                    totalStorageUsed = manager.getStorageUsed(),
                    freeSpace = manager.getAvailableSpace(),
                    readChapterCount = readChapters.size,
                    readChapterBytes = readChapters.sumOf { it.fileSizeBytes },
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Delete every completed-and-read chapter (≥80% read) — what the
     * cleanup banner offers. Files + DB rows go together via [MangaDownloadManager.cancelDownload].
     */
    fun clearReadChapters() {
        viewModelScope.launch {
            val toDelete = runCatching { dao.getReadCompletedDownloads() }.getOrDefault(emptyList())
            for (download in toDelete) {
                manager.cancelDownload(download.id)
            }
        }
    }

    fun deleteChapter(downloadId: String) {
        viewModelScope.launch {
            manager.cancelDownload(downloadId)
        }
    }

    fun deleteAllForManga(mangaId: Int) {
        viewModelScope.launch {
            manager.deleteAllForManga(mangaId)
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            manager.pauseDownload(downloadId)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch {
            manager.resumeDownload(downloadId)
            ani.saikou.data.local.downloads.DownloadService.start(
                // Need context — we'll use the app context
                AppModule.downloadManager().let { return@launch } // Simplified — service should be started from UI with context
            )
        }
    }
}

data class DownloadsUiState(
    val mangaList: List<MangaWithDownloads> = emptyList(),
    val totalStorageUsed: Long = 0,
    val freeSpace: Long = 0,
    val readChapterCount: Int = 0,
    val readChapterBytes: Long = 0,
    val isLoading: Boolean = true,
)

data class MangaWithDownloads(
    val manga: DownloadedMangaEntity,
    val chapters: List<DownloadEntity>,
)
