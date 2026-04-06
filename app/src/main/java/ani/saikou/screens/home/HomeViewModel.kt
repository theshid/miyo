package ani.saikou.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = AppModule.repository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userDeferred = async { repository.getUserData() }
            val watchingDeferred = async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred = async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }
            val recommendationsDeferred = async { repository.getRecommendations() }

            _uiState.value = HomeUiState(
                user = userDeferred.await(),
                continueWatching = watchingDeferred.await(),
                continueReading = readingDeferred.await(),
                recommendations = recommendationsDeferred.await(),
                isLoading = false,
            )
        }
    }
}

data class HomeUiState(
    val user: User? = null,
    val continueWatching: List<Media> = emptyList(),
    val continueReading: List<Media> = emptyList(),
    val recommendations: List<Media> = emptyList(),
    val isLoading: Boolean = true,
)
