package ani.saikou.screens.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserListsViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repository = AppModule.repository()
    private val type: String = savedStateHandle["type"] ?: "ANIME"

    private val _uiState = MutableStateFlow(UserListsUiState(type = type))
    val uiState: StateFlow<UserListsUiState> = _uiState

    init {
        loadList(tabs.first())
    }

    fun selectTab(tab: String) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        loadList(tab)
    }

    fun updateEntry(mediaId: Int, progress: Int?, score: Int?, status: String?) {
        viewModelScope.launch {
            repository.editListEntry(mediaId, progress, score, status)
            loadList(_uiState.value.selectedTab) // refresh
        }
    }

    private fun loadList(status: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val anilistStatus = statusMap[status] ?: "CURRENT"
            val items = if (type == "ANIME") {
                repository.getUserAnimeList(anilistStatus)
            } else {
                repository.getUserMangaList(anilistStatus)
            }
            _uiState.value = _uiState.value.copy(
                items = items,
                isLoading = false,
            )
        }
    }

    companion object {
        val tabs = listOf("Watching", "Completed", "Paused", "Planning", "Dropped")
        val statusMap = mapOf(
            "Watching" to "CURRENT",
            "Completed" to "COMPLETED",
            "Paused" to "PAUSED",
            "Planning" to "PLANNING",
            "Dropped" to "DROPPED",
        )
    }
}

data class UserListsUiState(
    val type: String = "ANIME",
    val selectedTab: String = "Watching",
    val items: List<Media> = emptyList(),
    val isLoading: Boolean = true,
)
