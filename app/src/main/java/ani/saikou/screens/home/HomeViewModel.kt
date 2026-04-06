package ani.saikou.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ani.saikou.data.local.db.ReadingHistoryEntity
import ani.saikou.di.AppModule
import ani.saikou.domain.model.Media
import ani.saikou.domain.model.User
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val repository = AppModule.repository()
    private val historyDao = AppModule.readingHistoryDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadHomeData()
        observeReadingHistory()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userDeferred = async { repository.getUserData() }
            val watchingDeferred = async { repository.getUserAnimeList("CURRENT") + repository.getUserAnimeList("REPEATING") }
            val readingDeferred = async { repository.getUserMangaList("CURRENT") + repository.getUserMangaList("REPEATING") }
            val recommendationsDeferred = async { repository.getRecommendations() }

            _uiState.value = _uiState.value.copy(
                user = userDeferred.await(),
                continueWatching = watchingDeferred.await(),
                continueReading = readingDeferred.await(),
                recommendations = recommendationsDeferred.await(),
                isLoading = false,
            )
        }
    }

    private fun observeReadingHistory() {
        viewModelScope.launch {
            historyDao.getRecent(10).collect { history ->
                _uiState.value = _uiState.value.copy(readingHistory = history)
            }
        }
    }
}

data class HomeUiState(
    val user: User? = null,
    val continueWatching: List<Media> = emptyList(),
    val continueReading: List<Media> = emptyList(),
    val recommendations: List<Media> = emptyList(),
    val readingHistory: List<ReadingHistoryEntity> = emptyList(),
    val isLoading: Boolean = true,
)
