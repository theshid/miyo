package ani.saikou.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.Download
import ani.saikou.domain.model.DownloadedManga
import ani.saikou.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadRepo: DownloadRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                downloadRepo.observeAllDownloadedManga(),
                downloadRepo.observeAllDownloads(),
            ) { manga, downloads ->
                val grouped = manga.map { m ->
                    MangaWithDownloads(
                        manga = m,
                        chapters = downloads.filter { it.mangaId == m.mangaId },
                    )
                }.filter { it.chapters.isNotEmpty() }

                // Re-query the evictable set every time downloads change. Cheap —
                // it's a small index-backed join inside the DAO.
                val evictable = downloadRepo.listEvictableReadChapters()

                DownloadsUiState(
                    mangaList = grouped,
                    totalStorageUsed = downloadRepo.storageUsedBytes(),
                    freeSpace = downloadRepo.availableSpaceBytes(),
                    readChapterCount = evictable.size,
                    readChapterBytes = evictable.sumOf { it.fileSizeBytes },
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Delete every completed-and-read chapter (≥80% read) — what the
     * cleanup banner offers. Files + DB rows go together.
     */
    fun clearReadChapters() {
        viewModelScope.launch {
            downloadRepo.evictReadChapters()
        }
    }

    fun deleteChapter(downloadId: String) {
        viewModelScope.launch {
            downloadRepo.cancelChapter(downloadId)
        }
    }

    fun deleteAllForManga(mangaId: Int) {
        viewModelScope.launch {
            downloadRepo.deleteAllForManga(mangaId)
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch {
            downloadRepo.pauseChapter(downloadId)
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
    val manga: DownloadedManga,
    val chapters: List<Download>,
)
