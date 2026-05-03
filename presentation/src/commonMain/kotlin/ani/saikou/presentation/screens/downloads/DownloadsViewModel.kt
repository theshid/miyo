package ani.saikou.presentation.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.MangaWithDownloads
import ani.saikou.domain.usecase.downloads.CancelChapterDownloadUseCase
import ani.saikou.domain.usecase.downloads.DeleteAllDownloadsForMangaUseCase
import ani.saikou.domain.usecase.downloads.EvictReadChaptersUseCase
import ani.saikou.domain.usecase.downloads.ObserveDownloadsUseCase
import ani.saikou.domain.usecase.downloads.PauseChapterDownloadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadsViewModel(
    observeDownloads: ObserveDownloadsUseCase,
    private val cancelChapter: CancelChapterDownloadUseCase,
    private val deleteMangaDownloads: DeleteAllDownloadsForMangaUseCase,
    private val evictReadChapters: EvictReadChaptersUseCase,
    private val pauseChapter: PauseChapterDownloadUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState

    init {
        viewModelScope.launch {
            observeDownloads().collect { snapshot ->
                _uiState.update {
                    DownloadsUiState(
                        mangaList = snapshot.mangaWithDownloads,
                        totalStorageUsed = snapshot.totalStorageUsed,
                        freeSpace = snapshot.freeSpace,
                        readChapterCount = snapshot.readChapterCount,
                        readChapterBytes = snapshot.readChapterBytes,
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Delete every completed-and-read chapter (≥80% read). */
    fun clearReadChapters() {
        viewModelScope.launch { evictReadChapters() }
    }

    fun deleteChapter(downloadId: String) {
        viewModelScope.launch { cancelChapter(downloadId) }
    }

    fun deleteAllForManga(mangaId: Int) {
        viewModelScope.launch { deleteMangaDownloads(mangaId) }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch { pauseChapter(downloadId) }
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
