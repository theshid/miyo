package ani.saikou.presentation.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.AnilistFailure
import ani.saikou.domain.model.Media
import ani.saikou.domain.usecase.anilist.GetAnimeDiscoveryUseCase
import ani.saikou.domain.usecase.anilist.LoadMorePopularAnimeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnimeViewModel(
    private val getDiscovery: GetAnimeDiscoveryUseCase,
    private val loadMorePopularPage: LoadMorePopularAnimeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadAnimeData()
    }

    fun loadAnimeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val snapshot = getDiscovery()
            popularPage = 1
            _uiState.update {
                AnimeUiState(
                    trending = snapshot.trending,
                    recentlyUpdated = snapshot.recentlyUpdated,
                    popular = snapshot.popular,
                    isLoading = false,
                    error = snapshot.failure?.let(::friendlyMessage),
                )
            }
        }
    }

    fun retry() {
        loadAnimeData()
    }

    private fun friendlyMessage(failure: AnilistFailure): String =
        when (failure) {
            is AnilistFailure.Network -> "Couldn't reach AniList. Check your connection."
            is AnilistFailure.Server -> "AniList is having issues (HTTP ${failure.httpStatus})."
            is AnilistFailure.Other -> "Something went wrong loading this page."
        }

    fun loadMorePopular() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            popularPage++
            val more = loadMorePopularPage(popularPage)
            _uiState.update { it.copy(popular = it.popular + more) }
            isLoadingMore = false
        }
    }
}

data class AnimeUiState(
    val trending: List<Media> = emptyList(),
    val recentlyUpdated: List<Media> = emptyList(),
    val popular: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
