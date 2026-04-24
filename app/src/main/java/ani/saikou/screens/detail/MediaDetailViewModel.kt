package ani.saikou.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.components.ChapterDownloadState
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Character
import ani.saikou.domain.model.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaDetailViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val downloadDao = AppModule.downloadDao()
    private val downloadManager = AppModule.downloadManager()
    private val mediaId: Int = savedStateHandle["id"] ?: 0

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState

    /** Map of chapter number → download status (`QUEUED`, `DOWNLOADING`, `COMPLETED`, `ERROR`, `PAUSED`). */
    val chapterDownloads: StateFlow<Map<Int, ChapterDownloadState>> = downloadDao
        .getDownloadsForManga(mediaId)
        .map { rows ->
            rows
                .filter { it.chapterNumber >= 0 }
                .associate { row ->
                    row.chapterNumber to ChapterDownloadState(
                        status = row.status,
                        downloadedPages = row.downloadedPages,
                        totalPages = row.totalPages,
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        loadMedia()
    }

    fun queueChapterDownload(chapterNumber: Int, onQueued: (() -> Unit)? = null) {
        val media = _uiState.value.media ?: return
        viewModelScope.launch {
            // totalPages and sourceId are placeholders — DownloadService re-resolves
            // the source and updates the row with the real page count when it runs.
            downloadManager.queueDownload(
                mangaId = media.id,
                mangaTitle = media.displayTitle,
                coverUrl = media.cover,
                chapterKey = chapterNumber.toString(),
                chapterNumber = chapterNumber,
                chapterName = "Chapter $chapterNumber",
                sourceId = "",
                totalPages = 0,
            )
            // Run AFTER the DB insert completes so the service sees the row.
            onQueued?.invoke()
        }
    }

    fun cancelChapterDownload(chapterNumber: Int) {
        val media = _uiState.value.media ?: return
        viewModelScope.launch {
            downloadManager.cancelDownload("${media.id}_$chapterNumber")
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val media = repository.getMedia(mediaId)
            _uiState.value = MediaDetailUiState(
                media = media,
                isLoading = false,
            )
        }
    }

    fun toggleFavorite() {
        val media = _uiState.value.media ?: return
        viewModelScope.launch {
            val isAnime = media.type == "ANIME"
            repository.toggleFavorite(media.id, isAnime)
            _uiState.value = _uiState.value.copy(
                media = media.copy(isFav = !media.isFav),
            )
            ListEventBus.emit(ListEvent.FavoriteToggled(media.id))
        }
    }

    fun updateProgress(progress: Int) {
        val media = _uiState.value.media ?: return
        viewModelScope.launch {
            repository.editListEntry(mediaId = media.id, progress = progress)
            _uiState.value = _uiState.value.copy(
                media = media.copy(userProgress = progress),
            )
            ListEventBus.emit(ListEvent.ProgressUpdated(media.id, progress))
        }
    }

    fun updateStatus(status: String) {
        val media = _uiState.value.media ?: return
        viewModelScope.launch {
            repository.editListEntry(mediaId = media.id, status = status)
            _uiState.value = _uiState.value.copy(
                media = media.copy(userStatus = status),
            )
            ListEventBus.emit(ListEvent.ListEntryChanged(media.id, status))
        }
    }

    fun removeFromList() {
        val media = _uiState.value.media ?: return
        val listId = media.userListEntryId ?: return
        viewModelScope.launch {
            repository.deleteListEntry(listId)
            _uiState.value = _uiState.value.copy(
                media = media.copy(
                    userStatus = null,
                    userListEntryId = null,
                    userProgress = null,
                    userScore = 0,
                ),
            )
            ListEventBus.emit(ListEvent.ListEntryChanged(media.id, null))
        }
    }
}

data class MediaDetailUiState(
    val media: Media? = null,
    val isLoading: Boolean = true,
)
