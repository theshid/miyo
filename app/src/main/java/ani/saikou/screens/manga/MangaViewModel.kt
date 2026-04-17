package ani.saikou.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaViewModel : ViewModel() {

    private val repository = AppModule.repository()

    private val _uiState = MutableStateFlow(MangaUiState())
    val uiState: StateFlow<MangaUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadMangaData()
    }

    fun loadMangaData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val trendingDeferred = async { repository.getTrendingManga() }
            val updatedDeferred = async { repository.getRecentlyUpdatedManga() }
            val popularDeferred = async { repository.getPopularManga() }

            _uiState.value = MangaUiState(
                trending = trendingDeferred.await(),
                recentlyUpdated = updatedDeferred.await(),
                popular = popularDeferred.await(),
                isLoading = false,
            )
        }
    }

    fun loadMorePopular() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            popularPage++
            val more = repository.getPopularManga(page = popularPage)
            _uiState.value = _uiState.value.copy(
                popular = _uiState.value.popular + more,
            )
            isLoadingMore = false
        }
    }
}

data class MangaUiState(
    val trending: List<Media> = emptyList(),
    val recentlyUpdated: List<Media> = emptyList(),
    val popular: List<Media> = emptyList(),
    val isLoading: Boolean = true,
)
