package ani.saikou.presentation.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.domain.model.AnilistFailure
import ani.saikou.domain.model.Media
import ani.saikou.domain.usecase.anilist.GetMangaDiscoveryUseCase
import ani.saikou.domain.usecase.anilist.LoadMorePopularMangaUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MangaViewModel(
    private val getDiscovery: GetMangaDiscoveryUseCase,
    private val loadMorePopularPage: LoadMorePopularMangaUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MangaUiState())
    val uiState: StateFlow<MangaUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadMangaData()
    }

    fun loadMangaData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val snapshot = getDiscovery()
            popularPage = 1
            _uiState.update {
                MangaUiState(
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
        loadMangaData()
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

data class MangaUiState(
    val trending: List<Media> = emptyList(),
    val recentlyUpdated: List<Media> = emptyList(),
    val popular: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
