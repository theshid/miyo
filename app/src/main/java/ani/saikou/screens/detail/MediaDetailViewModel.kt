package ani.saikou.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.ListEvent
import ani.saikou.data.local.ListEventBus
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Character
import ani.saikou.domain.model.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MediaDetailViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val mediaId: Int = savedStateHandle["id"] ?: 0

    private val _uiState = MutableStateFlow(MediaDetailUiState())
    val uiState: StateFlow<MediaDetailUiState> = _uiState

    init {
        loadMedia()
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
