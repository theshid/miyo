package ani.saikou.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnimeViewModel : ViewModel() {

    private val repository = AppModule.repository()

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadAnimeData()
    }

    fun loadAnimeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val trendingDeferred = async { repository.getTrendingAnime(perPage = 8) }
            val updatedDeferred = async { repository.getRecentlyUpdatedAnime() }
            val popularDeferred = async { repository.getPopularAnime(page = 1, perPage = 10) }

            _uiState.value = AnimeUiState(
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
            val more = repository.getPopularAnime(page = popularPage)
            _uiState.value = _uiState.value.copy(
                popular = _uiState.value.popular + more,
            )
            isLoadingMore = false
        }
    }

    private suspend fun AnilistRepository.getTrendingAnime(perPage: Int): List<Media> =
        getTrendingAnime(1)

    private suspend fun AnilistRepository.getPopularAnime(page: Int, perPage: Int): List<Media> =
        getPopularAnime(page)
}

data class AnimeUiState(
    val trending: List<Media> = emptyList(),
    val recentlyUpdated: List<Media> = emptyList(),
    val popular: List<Media> = emptyList(),
    val isLoading: Boolean = true,
)
