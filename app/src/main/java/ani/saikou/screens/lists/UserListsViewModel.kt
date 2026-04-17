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

    /** Re-fetch the currently-selected tab (called on screen resume). */
    fun refresh() {
        loadList(_uiState.value.selectedTab)
    }

    fun updateEntry(mediaId: Int, progress: Int?, score: Int?, status: String?) {
        viewModelScope.launch {
            repository.editListEntry(mediaId, progress, score, status)
            loadList(_uiState.value.selectedTab) // refresh
        }
    }

    private fun loadList(tab: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = if (tab == FAVORITES_TAB) {
                repository.getUserFavorites(type)
            } else {
                val anilistStatus = statusMap[tab] ?: "CURRENT"
                if (type == "ANIME") {
                    repository.getUserAnimeList(anilistStatus)
                } else {
                    repository.getUserMangaList(anilistStatus)
                }
            }
            _uiState.value = _uiState.value.copy(
                items = items,
                isLoading = false,
            )
        }
    }

    companion object {
        const val FAVORITES_TAB = "Favorites"
        val tabs = listOf("Watching", "Completed", "Paused", "Planning", "Dropped", FAVORITES_TAB)
        // Only the list-status tabs — excludes Favorites, which isn't a MediaListStatus.
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
