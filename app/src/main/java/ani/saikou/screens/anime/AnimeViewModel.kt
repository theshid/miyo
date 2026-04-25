package ani.saikou.screens.anime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.remote.AnilistFailure
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnimeViewModel : ViewModel() {

    private val repository = AppModule.repository()
    private val api = AppModule.anilistApi()

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState

    private var popularPage = 1
    private var isLoadingMore = false

    init {
        loadAnimeData()
    }

    fun loadAnimeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val trendingDeferred = async { repository.getTrendingAnime() }
            val updatedDeferred = async { repository.getRecentlyUpdatedAnime() }
            val popularDeferred = async { repository.getPopularAnime(page = 1) }

            val trending = trendingDeferred.await()
            val updated = updatedDeferred.await()
            val popular = popularDeferred.await()

            // The api signals retry-exhausted failures via lastFailure. If it
            // fired AND we got nothing from the network, surface it to the user.
            val networkFailure = api.lastFailure.value
            val allEmpty = trending.isEmpty() && updated.isEmpty() && popular.isEmpty()
            val errorMessage = if (networkFailure != null && allEmpty) {
                friendlyMessage(networkFailure)
            } else null

            _uiState.value = AnimeUiState(
                trending = trending,
                recentlyUpdated = updated,
                popular = popular,
                isLoading = false,
                error = errorMessage,
            )

            // AniList always has trending/popular/recently-updated anime, so
            // three empty lists with no network failure flag still means
            // something is off — keep the existing Sentry breadcrumb.
            if (allEmpty && networkFailure == null) {
                reportEmptyAnimeTab()
            }
        }
    }

    fun retry() {
        loadAnimeData()
    }

    private fun friendlyMessage(failure: AnilistFailure): String = when (failure) {
        is AnilistFailure.Network -> "Couldn't reach AniList. Check your connection."
        is AnilistFailure.Server -> "AniList is having issues (HTTP ${failure.httpStatus})."
        is AnilistFailure.Other -> "Something went wrong loading this page."
    }

    private fun reportEmptyAnimeTab() {
        try {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                scope.setTag("area", "AnimeTab")
                Sentry.captureMessage("Anime tab rendered empty (all 3 AniList sections returned 0 results)")
            }
        } catch (_: Exception) { /* best-effort */ }
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

}

data class AnimeUiState(
    val trending: List<Media> = emptyList(),
    val recentlyUpdated: List<Media> = emptyList(),
    val popular: List<Media> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)
