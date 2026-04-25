package ani.saikou.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.AnilistFailure
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaViewModel : ViewModel() {

    private val repository = AppModule.repository()
    private val api = AppModule.anilistApi()

    private val _uiState = MutableStateFlow(MangaUiState())
    val uiState: StateFlow<MangaUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadMangaData()
    }

    fun loadMangaData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val trendingDeferred = async { repository.getTrendingManga() }
            val updatedDeferred = async { repository.getRecentlyUpdatedManga() }
            val popularDeferred = async { repository.getPopularManga() }

            val trending = trendingDeferred.await()
            val updated = updatedDeferred.await()
            val popular = popularDeferred.await()

            val networkFailure = api.lastFailure.value
            val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()

            _uiState.value = MangaUiState(
                trending = trending,
                recentlyUpdated = updated,
                popular = popular,
                isLoading = false,
                error = if (networkFailure != null && allEmpty) friendlyMessage(networkFailure) else null,
            )
        }
    }

    fun retry() {
        loadMangaData()
    }

    private fun friendlyMessage(failure: AnilistFailure): String = when (failure) {
        is AnilistFailure.Network -> "Couldn't reach AniList. Check your connection."
        is AnilistFailure.Server -> "AniList is having issues (HTTP ${failure.httpStatus})."
        is AnilistFailure.Other -> "Something went wrong loading this page."
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
    val error: String? = null,
)
